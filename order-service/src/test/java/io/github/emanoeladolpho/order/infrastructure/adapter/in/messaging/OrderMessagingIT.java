package io.github.emanoeladolpho.order.infrastructure.adapter.in.messaging;

import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import io.github.emanoeladolpho.order.infrastructure.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static io.github.emanoeladolpho.order.infrastructure.KafkaTestSupport.awaitRecord;
import static io.github.emanoeladolpho.order.infrastructure.KafkaTestSupport.header;
import static io.github.emanoeladolpho.order.infrastructure.KafkaTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fluxo de mensageria do order-service com PostgreSQL e Kafka reais:
 * outbox → Kafka, consumo idempotente dos eventos de pagamento e Dead Letter Topic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.config.name=order-service", "chassis.outbox.poll-interval=100ms",
        "chassis.consumer.max-retries=1", "chassis.consumer.initial-backoff=100ms"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureObservability
class OrderMessagingIT {

    @Autowired
    private PlaceOrderUseCase placeOrder;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ConsumerFactory<String, String> consumerFactory;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestRestTemplate http;

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    private Order placeOrder() {
        var command = new PlaceOrderUseCase.Command(Requester.customer("customer-it"), UUID.randomUUID().toString(),
                "BRL", List.of(new PlaceOrderUseCase.Item("SKU-1", "Teclado", new BigDecimal("199.90"), 1)));
        return placeOrder.place(command).order();
    }

    private String paymentEvent(UUID eventId, String type, Order order, String reason) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"2030-01-01T00:00:00Z",
                 "data":{"paymentId":"%s","orderId":"%s","reason":%s}}
                """.formatted(eventId, type, UUID.randomUUID(), order.getId(),
                reason == null ? "null" : "\"" + reason + "\"");
    }

    @Test
    void orderCreatedShouldBePublishedFromOutboxWithKeyAndCorrelationId() throws Exception {
        CorrelationId.set("corr-it-1");
        Order order = placeOrder();

        ConsumerRecord<String, String> record = awaitRecord(consumerFactory, "order-events",
                r -> order.getId().toString().equals(r.key()));

        JsonNode envelope = objectMapper.readTree(record.value());
        assertThat(envelope.get("eventType").asText()).isEqualTo("OrderCreated");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.at("/data/totalAmount").decimalValue()).isEqualByComparingTo("199.90");
        assertThat(envelope.at("/data/items/0/sku").asText()).isEqualTo("SKU-1");
        assertThat(header(record, "X-Correlation-Id")).isEqualTo("corr-it-1");
        assertThat(header(record, "eventType")).isEqualTo("OrderCreated");

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select count(*) from outbox_events where id = ? and published_at is not null", Integer.class,
                UUID.fromString(envelope.get("eventId").asText())) == 1);
    }

    @Test
    void paymentEventsShouldMoveOrderToPaidAndDuplicatesShouldBeIgnored() throws Exception {
        Order order = placeOrder();
        UUID initiated = UUID.randomUUID();
        UUID approved = UUID.randomUUID();

        send(kafkaTemplate, "payment-events", order.getId().toString(),
                paymentEvent(initiated, "PaymentInitiated", order, null), "corr-it-2");
        send(kafkaTemplate, "payment-events", order.getId().toString(),
                paymentEvent(approved, "PaymentApproved", order, null), "corr-it-2");
        send(kafkaTemplate, "payment-events", order.getId().toString(),
                paymentEvent(approved, "PaymentApproved", order, null), "corr-it-2");

        await().atMost(Duration.ofSeconds(20))
                .until(() -> orders.findById(order.getId()).orElseThrow().getStatus() == OrderStatus.PAID);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select count(*) from processed_events where event_id in (?, ?)", Integer.class,
                initiated, approved) == 2);
    }

    @Test
    void declinedPaymentShouldFailOrderWithReason() throws Exception {
        Order order = placeOrder();

        send(kafkaTemplate, "payment-events", order.getId().toString(),
                paymentEvent(UUID.randomUUID(), "PaymentDeclined", order, "Saldo insuficiente"), null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Order current = orders.findById(order.getId()).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(current.getFailureReason()).isEqualTo("Saldo insuficiente");
        });
    }

    @Test
    void malformedMessageShouldGoToDeadLetterTopic() throws Exception {
        String key = "malformed-" + UUID.randomUUID();
        send(kafkaTemplate, "payment-events", key, "{isto não é json", null);

        ConsumerRecord<String, String> dead = awaitRecord(consumerFactory, "payment-events-dlt",
                r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).contains("MalformedEventException");
    }

    @Test
    void eventForUnknownOrderShouldBeRetriedAndThenSentToDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = """
                {"eventId":"%s","eventType":"PaymentApproved","eventVersion":1,"occurredAt":"2030-01-01T00:00:00Z",
                 "data":{"paymentId":"%s","orderId":"%s"}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), key);
        send(kafkaTemplate, "payment-events", key, payload, null);

        ConsumerRecord<String, String> dead = awaitRecord(consumerFactory, "payment-events-dlt",
                r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-message")).contains("Pedido não encontrado");
    }

    @Test
    void probesAndMetricsShouldBePublicAndErrorsShouldCarryTheCorrelationId() throws Exception {
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        var headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "corr-obs-1");
        var unauthorized = http.exchange("/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-obs-1");
        assertThat(objectMapper.readTree(unauthorized.getBody()).get("correlationId").asText()).isEqualTo("corr-obs-1");

        String metrics = http.getForObject("/actuator/prometheus", String.class);
        assertThat(metrics)
                .contains("application=\"order-service\"")
                .contains("http_server_requests_seconds_bucket")
                .contains("outbox_pending_events");
    }
}
