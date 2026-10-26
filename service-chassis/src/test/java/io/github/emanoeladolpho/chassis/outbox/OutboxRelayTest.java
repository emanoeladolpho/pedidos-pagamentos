package io.github.emanoeladolpho.chassis.outbox;

import io.github.emanoeladolpho.chassis.ChassisProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Caminhos de falha do relay, difíceis de provocar com um Kafka real. */
class OutboxRelayTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate,
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            new ChassisProperties(new ChassisProperties.Outbox(Duration.ofMillis(100), 2, Duration.ofSeconds(1)),
                    null, null),
            meterRegistry);

    private static OutboxEventEntity event() {
        return new OutboxEventEntity(new OutboxMessage(UUID.randomUUID(), "OrderCreated", "order-events", "key",
                "{}"), null);
    }

    @Test
    void shouldStopBatchAtFirstFailureKeepingOrderAndRecordingTheError() {
        OutboxEventEntity first = event();
        OutboxEventEntity second = event();
        when(repository.lockNextPending(2)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker fora")));

        relay.publishPending();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker fora");
        assertThat(second.getAttempts()).isZero();
        verify(kafkaTemplate, times(1)).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    @Test
    void shouldKeepPollingWhileBatchesAreFull() {
        OutboxEventEntity a = event();
        OutboxEventEntity b = event();
        OutboxEventEntity c = event();
        when(repository.lockNextPending(2)).thenReturn(List.of(a, b), List.of(c));
        when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        assertThat(List.of(a, b, c)).allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
        assertThat(meterRegistry.get("outbox.events.published").counter().count()).isEqualTo(3);
    }
}
