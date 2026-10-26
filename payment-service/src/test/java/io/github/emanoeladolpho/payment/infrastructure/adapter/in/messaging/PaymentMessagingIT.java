package io.github.emanoeladolpho.payment.infrastructure.adapter.in.messaging;

import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;
import io.github.emanoeladolpho.payment.infrastructure.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static io.github.emanoeladolpho.payment.infrastructure.KafkaTestSupport.awaitRecord;
import static io.github.emanoeladolpho.payment.infrastructure.KafkaTestSupport.header;
import static io.github.emanoeladolpho.payment.infrastructure.KafkaTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fluxo de mensageria do payment-service com PostgreSQL e Kafka reais. O gateway simulado usa valores
 * "mágicos": centavos .13 → recusa, .66 → falha técnica.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.config.name=payment-service", "chassis.outbox.poll-interval=100ms",
        "chassis.consumer.max-retries=1", "chassis.consumer.initial-backoff=100ms",
        "app.gateway.simulated.latency=0ms"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureObservability
class PaymentMessagingIT {

    @Autowired
    private PaymentRepository payments;
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

    private static String orderCreated(UUID eventId, String orderId, String amount) {
        return """
                {"eventId":"%s","eventType":"OrderCreated","eventVersion":1,"occurredAt":"2030-01-01T00:00:00Z",
                 "data":{"orderId":"%s","customerId":"customer-it","totalAmount":%s,"currency":"BRL",
                         "items":[{"sku":"SKU-1","productName":"Teclado","unitPrice":%s,"quantity":1}],
                         "campoNovo":"ignorado"}}
                """.formatted(eventId, orderId, amount, amount);
    }

    private static String orderCancelled(String orderId) {
        return """
                {"eventId":"%s","eventType":"OrderCancelled","eventVersion":1,"occurredAt":"2030-01-01T00:00:00Z",
                 "data":{"orderId":"%s","customerId":"customer-it","previousStatus":"PAID","reason":"Desistência"}}
                """.formatted(UUID.randomUUID(), orderId);
    }

    private Optional<Payment> paymentOf(String orderId) {
        return payments.findByOrderId(OrderId.of(orderId));
    }

    private JsonNode awaitPaymentEvent(String orderId, String eventType) throws Exception {
        ConsumerRecord<String, String> record = awaitRecord(consumerFactory, "payment-events",
                r -> orderId.equals(r.key()) && eventType.equals(header(r, "eventType")));
        return objectMapper.readTree(record.value());
    }

    @Test
    void orderCreatedShouldApprovePaymentOnceEvenWhenRedelivered() throws Exception {
        String orderId = UUID.randomUUID().toString();
        UUID eventId = UUID.randomUUID();

        send(kafkaTemplate, "order-events", orderId, orderCreated(eventId, orderId, "199.90"), "corr-pay-1");
        send(kafkaTemplate, "order-events", orderId, orderCreated(eventId, orderId, "199.90"), "corr-pay-1");

        await().atMost(Duration.ofSeconds(20))
                .until(() -> paymentOf(orderId).map(p -> p.getStatus() == PaymentStatus.APPROVED).orElse(false));

        ConsumerRecord<String, String> approved = awaitRecord(consumerFactory, "payment-events",
                r -> orderId.equals(r.key()) && "PaymentApproved".equals(header(r, "eventType")));
        assertThat(header(approved, "X-Correlation-Id")).isEqualTo("corr-pay-1");
        JsonNode envelope = objectMapper.readTree(approved.value());
        assertThat(envelope.at("/data/amount").decimalValue()).isEqualByComparingTo("199.90");
        assertThat(envelope.at("/data/gatewayTransactionId").asText()).startsWith("gw-");
        assertThat(awaitPaymentEvent(orderId, "PaymentInitiated").at("/data/orderId").asText()).isEqualTo(orderId);

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select count(*) from processed_events where event_id = ?", Integer.class, eventId) == 1);
        assertThat(jdbc.queryForObject("select count(*) from payments where order_id = ?", Integer.class,
                UUID.fromString(orderId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where message_key = ? and event_type = ?",
                Integer.class, orderId, "PaymentApproved")).isEqualTo(1);
    }

    @Test
    void declinedChargeShouldPublishPaymentDeclined() throws Exception {
        String orderId = UUID.randomUUID().toString();

        send(kafkaTemplate, "order-events", orderId, orderCreated(UUID.randomUUID(), orderId, "50.13"), null);

        JsonNode declined = awaitPaymentEvent(orderId, "PaymentDeclined");
        assertThat(declined.at("/data/reason").asText()).isEqualTo("Pagamento recusado pelo emissor");
        assertThat(paymentOf(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void gatewayOutageShouldSendEventToDeadLetterTopicKeepingPaymentPending() throws Exception {
        String orderId = UUID.randomUUID().toString();

        send(kafkaTemplate, "order-events", orderId, orderCreated(UUID.randomUUID(), orderId, "10.66"), null);

        ConsumerRecord<String, String> dead = awaitRecord(consumerFactory, "order-events-dlt",
                r -> orderId.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).contains("PaymentGatewayUnavailableException");
        assertThat(paymentOf(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancellationOfPaidOrderShouldRefundPayment() throws Exception {
        String orderId = UUID.randomUUID().toString();
        send(kafkaTemplate, "order-events", orderId, orderCreated(UUID.randomUUID(), orderId, "80.00"), null);
        send(kafkaTemplate, "order-events", orderId, orderCancelled(orderId), null);

        JsonNode refunded = awaitPaymentEvent(orderId, "PaymentRefunded");
        assertThat(refunded.at("/data/reason").asText()).isEqualTo("Desistência");
        assertThat(paymentOf(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void malformedOrderEventShouldGoToDeadLetterTopic() throws Exception {
        String key = "malformed-" + UUID.randomUUID();
        send(kafkaTemplate, "order-events", key,
                "{\"eventId\":\"x\",\"eventType\":\"OrderCreated\",\"data\":{}}", null);

        ConsumerRecord<String, String> dead = awaitRecord(consumerFactory, "order-events-dlt",
                r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).contains("MalformedEventException");
    }

    @Test
    void probesAndMetricsShouldBePublicAndErrorsShouldCarryTheCorrelationId() throws Exception {
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        var headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "corr-obs-1");
        var unauthorized = http.exchange("/payments", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-obs-1");
        assertThat(objectMapper.readTree(unauthorized.getBody()).get("correlationId").asText()).isEqualTo("corr-obs-1");

        String metrics = http.getForObject("/actuator/prometheus", String.class);
        assertThat(metrics)
                .contains("application=\"payment-service\"")
                .contains("http_server_requests_seconds_bucket")
                .contains("outbox_pending_events")
                .contains("resilience4j_circuitbreaker_state{application=\"payment-service\",name=\"payment-gateway\"");
    }
}
