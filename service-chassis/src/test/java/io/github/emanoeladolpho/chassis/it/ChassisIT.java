package io.github.emanoeladolpho.chassis.it;

import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import io.github.emanoeladolpho.chassis.inbox.Inbox;
import io.github.emanoeladolpho.chassis.outbox.OutboxMessage;
import io.github.emanoeladolpho.chassis.outbox.OutboxWriter;
import io.github.emanoeladolpho.chassis.web.BaseExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Outbox, inbox, correlation id e Dead Letter Topic do chassis contra PostgreSQL e Kafka reais. */
@SpringBootTest(classes = ChassisTestApplication.class, properties = {
        "spring.liquibase.change-log=classpath:db/changelog/chassis/001-outbox-and-inbox.yaml",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "chassis.outbox.poll-interval=100ms",
        "chassis.consumer.max-retries=2",
        "chassis.consumer.initial-backoff=50ms",
        "chassis.security.jwk-set-uri=http://localhost:1/certs",
        "chassis.security.issuer=http://localhost:1/realms/test"})
@Import(ChassisIT.Containers.class)
class ChassisIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:3.9.1");
        }
    }

    @Autowired
    private OutboxWriter outboxWriter;
    @Autowired
    private Inbox inbox;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ConsumerFactory<String, String> consumerFactory;
    @Autowired
    private ChassisTestApplication.RecordingListener listener;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private ApplicationContext context;

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(ChassisTestApplication.TOPIC + "-dlt"));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r);
                    }
                });
                return !found.isEmpty();
            });
            return found.getFirst();
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void outboxMessageShouldBePublishedWithCorrelationIdAndReachTheConsumerMdc() {
        UUID eventId = UUID.randomUUID();
        String payload = "evento-" + eventId;
        CorrelationId.set("corr-chassis");

        transactionTemplate.executeWithoutResult(status -> outboxWriter.append(
                new OutboxMessage(eventId, "TestEvent", ChassisTestApplication.TOPIC, "key-1", "\"" + payload + "\"")));

        await().atMost(Duration.ofSeconds(20))
                .until(() -> listener.received.contains("\"" + payload + "\"|corr-chassis"));
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
                "select count(*) from outbox_events where id = ? and published_at is not null", Integer.class,
                eventId) == 1);
        assertThat(meterRegistry.get("outbox.pending.events").gauge().value()).isZero();
    }

    @Test
    void outboxWriterShouldRequireAnExistingTransaction() {
        var message = new OutboxMessage(UUID.randomUUID(), "TestEvent", ChassisTestApplication.TOPIC, "k", "{}");

        assertThatThrownBy(() -> outboxWriter.append(message)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void inboxShouldDetectDuplicatesThroughThePrimaryKey() {
        UUID eventId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> inbox.markProcessed(eventId, "consumer-a"));

        assertThat(transactionTemplate.<Boolean>execute(status -> inbox.isProcessed(eventId, "consumer-a"))).isTrue();
        assertThat(transactionTemplate.<Boolean>execute(status -> inbox.isProcessed(eventId, "consumer-b"))).isFalse();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> inbox.markProcessed(eventId, "consumer-a")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void malformedMessageShouldGoStraightToDeadLetterTopic() throws Exception {
        String key = "malformed-" + UUID.randomUUID();
        kafkaTemplate.send(ChassisTestApplication.TOPIC, key, "malformed").get();

        ConsumerRecord<String, String> dead = awaitDeadLetter(key);

        assertThat(dead.value()).isEqualTo("malformed");
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).contains("MalformedEventException");
    }

    @Test
    void transientFailureShouldBeRetriedBeforeGoingToDeadLetterTopic() throws Exception {
        String key = "transient-" + UUID.randomUUID();
        int before = listener.transientFailures.get();
        kafkaTemplate.send(ChassisTestApplication.TOPIC, key, "transient").get();

        awaitDeadLetter(key);

        assertThat(listener.transientFailures.get() - before).isEqualTo(3);
    }

    @Test
    void securityAndWebBeansShouldBeAutoConfigured() {
        assertThat(context.getBean(JwtDecoder.class)).isNotNull();
        assertThat(context.getBean(BaseExceptionHandler.class)).isNotNull();
    }
}
