package io.github.emanoeladolpho.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.emanoeladolpho.order.OrderServiceApplication;
import io.github.emanoeladolpho.payment.PaymentServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fluxo completo com os dois serviços reais no mesmo teste, cada um com seu PostgreSQL, conversando por um
 * Kafka real e autenticando com tokens JWT assinados: pedido → pagamento → PAID, recusa → FAILED,
 * cancelamento → estorno, e estorno manual do ADMIN → pedido cancelado.
 */
class OrderPaymentFlowE2EIT {

    private static final PostgreSQLContainer<?> ORDERS_DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders");
    private static final PostgreSQLContainer<?> PAYMENTS_DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payments");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static TestIdentityProvider identity;
    private static ConfigurableApplicationContext orderService;
    private static ConfigurableApplicationContext paymentService;
    private static String orderUrl;
    private static String paymentUrl;

    private final String customer = "cliente-" + UUID.randomUUID();
    private final String customerToken = identity.token(customer, "USER");
    private final String adminToken = identity.token("admin-e2e", "USER", "ADMIN");

    @BeforeAll
    static void startEverything() throws Exception {
        Stream.of(ORDERS_DB, PAYMENTS_DB, KAFKA).parallel().forEach(container -> container.start());
        identity = new TestIdentityProvider();
        orderService = start(OrderServiceApplication.class, OrderServiceApplication.CONFIG_NAME, ORDERS_DB);
        paymentService = start(PaymentServiceApplication.class, PaymentServiceApplication.CONFIG_NAME, PAYMENTS_DB,
                "--app.gateway.simulated.latency=0ms");
        orderUrl = "http://localhost:" + port(orderService);
        paymentUrl = "http://localhost:" + port(paymentService);
    }

    @AfterAll
    static void stopEverything() {
        Stream.of(orderService, paymentService).filter(ctx -> ctx != null).forEach(ConfigurableApplicationContext::close);
        if (identity != null) {
            identity.close();
        }
        Stream.of(KAFKA, PAYMENTS_DB, ORDERS_DB).forEach(container -> container.stop());
    }

    /** Argumentos de linha de comando têm precedência sobre o YAML do serviço, como as variáveis no compose. */
    private static ConfigurableApplicationContext start(Class<?> application, String configNameProperty,
                                                        PostgreSQLContainer<?> database, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--" + configNameProperty,
                "--server.port=0",
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(),
                "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                "--chassis.security.jwk-set-uri=" + identity.jwkSetUri(),
                "--chassis.security.issuer=" + identity.issuer(),
                "--chassis.outbox.poll-interval=100ms"));
        args.addAll(List.of(extra));
        return new SpringApplicationBuilder(application).run(args.toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext context) {
        return ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    private record Response(int status, JsonNode body, HttpResponse<String> raw) {
    }

    private static Response send(HttpRequest.Builder request, String token) throws IOException, InterruptedException {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = response.body().isBlank() ? null : JSON.readTree(response.body());
        return new Response(response.statusCode(), body, response);
    }

    private static Response get(String url, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), token);
    }

    private static Response post(String url, String token, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return send(request, token);
    }

    /** Pedido com dois teclados e um mouse de 50,00: total = 2 × unitPrice + 50,00. */
    private Response placeOrder(String unitPrice, String idempotencyKey, String correlationId) throws Exception {
        return placeOrderWithItems(idempotencyKey, correlationId, """
                {"sku":"TECLADO-01","productName":"Teclado mecânico","unitPrice":%s,"quantity":2},
                {"sku":"MOUSE-01","productName":"Mouse sem fio","unitPrice":50.00,"quantity":1}
                """.formatted(unitPrice));
    }

    private Response placeOrderWithItems(String idempotencyKey, String correlationId, String itemsJson) throws Exception {
        return post(orderUrl + "/orders", customerToken, "{\"currency\":\"BRL\",\"items\":[" + itemsJson + "]}",
                "Idempotency-Key", idempotencyKey, "X-Correlation-Id", correlationId);
    }

    private String awaitOrderStatus(String orderId, String expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(
                () -> expected.equals(get(orderUrl + "/orders/" + orderId, customerToken).body().get("status").asText()));
        return expected;
    }

    private JsonNode awaitPayment(String orderId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            Response payment = get(paymentUrl + "/payments?orderId=" + orderId, customerToken);
            return payment.status() == 200 && expectedStatus.equals(payment.body().get("status").asText());
        });
        try {
            return get(paymentUrl + "/payments?orderId=" + orderId, customerToken).body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void orderShouldBePaidAndCorrelationIdShouldCrossBothServices() throws Exception {
        String key = UUID.randomUUID().toString();
        String correlationId = "e2e-" + UUID.randomUUID();

        Response created = placeOrder("100.00", key, correlationId);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("totalAmount").decimalValue()).isEqualByComparingTo("250.00");
        String orderId = created.body().get("id").asText();

        Response replay = placeOrder("100.00", key, correlationId);
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body().get("id").asText()).isEqualTo(orderId);
        assertThat(replay.raw().headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(placeOrder("999.00", key, correlationId).status()).isEqualTo(422);

        awaitOrderStatus(orderId, "PAID");
        JsonNode payment = awaitPayment(orderId, "APPROVED");
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(payment.get("customerId").asText()).isEqualTo(customer);

        JdbcTemplate paymentsDb = paymentService.getBean(JdbcTemplate.class);
        assertThat(paymentsDb.queryForList(
                "select distinct correlation_id from outbox_events where message_key = ?", String.class, orderId))
                .containsExactly(correlationId);
    }

    @Test
    void declinedPaymentShouldFailTheOrder() throws Exception {
        // total com centavos .13: o gateway simulado recusa
        String orderId = placeOrderWithItems(UUID.randomUUID().toString(), "e2e-declined",
                "{\"sku\":\"MONITOR-01\",\"productName\":\"Monitor\",\"unitPrice\":899.13,\"quantity\":1}")
                .body().get("id").asText();

        awaitOrderStatus(orderId, "FAILED");
        assertThat(get(orderUrl + "/orders/" + orderId, customerToken).body().get("failureReason").asText())
                .isEqualTo("Pagamento recusado pelo emissor");
        awaitPayment(orderId, "DECLINED");
    }

    @Test
    void cancellingAPaidOrderShouldRefundThePayment() throws Exception {
        String orderId = placeOrder("80.00", UUID.randomUUID().toString(), "e2e-cancel").body().get("id").asText();
        awaitOrderStatus(orderId, "PAID");

        Response cancelled = post(orderUrl + "/orders/" + orderId + "/cancel", customerToken,
                "{\"reason\":\"Desisti da compra\"}");

        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.body().get("status").asText()).isEqualTo("CANCELLED");
        awaitPayment(orderId, "REFUNDED");
    }

    @Test
    void adminRefundShouldCancelTheOrderAndCustomersShouldBeIsolated() throws Exception {
        String orderId = placeOrder("120.00", UUID.randomUUID().toString(), "e2e-admin").body().get("id").asText();
        awaitOrderStatus(orderId, "PAID");
        String paymentId = awaitPayment(orderId, "APPROVED").get("id").asText();

        String otherCustomer = identity.token("outro-" + UUID.randomUUID(), "USER");
        assertThat(get(orderUrl + "/orders/" + orderId, otherCustomer).status()).isEqualTo(404);
        assertThat(get(paymentUrl + "/payments/" + paymentId, otherCustomer).status()).isEqualTo(404);
        assertThat(post(paymentUrl + "/payments/" + paymentId + "/refund", customerToken,
                "{\"reason\":\"tentativa\"}").status()).isEqualTo(403);
        assertThat(get(orderUrl + "/orders", null).status()).isEqualTo(401);

        Response refunded = post(paymentUrl + "/payments/" + paymentId + "/refund", adminToken,
                "{\"reason\":\"Produto com defeito\"}");

        assertThat(refunded.status()).isEqualTo(200);
        assertThat(refunded.body().get("status").asText()).isEqualTo("REFUNDED");
        awaitOrderStatus(orderId, "CANCELLED");
        assertThat(get(orderUrl + "/orders/" + orderId, adminToken).body().get("cancellationReason").asText())
                .isEqualTo("Produto com defeito");
    }
}
