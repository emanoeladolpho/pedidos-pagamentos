package io.github.emanoeladolpho.chassis.outbox;

import io.github.emanoeladolpho.chassis.ChassisProperties;
import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publica no Kafka os eventos pendentes do outbox (entrega "at-least-once").
 * <p>
 * Se o envio falhar, o lote para no primeiro erro (preserva a ordem) e tenta de novo na próxima
 * varredura. Se o serviço cair depois de enviar e antes de marcar como publicado, o evento é
 * reenviado — por isso os consumidores são idempotentes.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ChassisProperties.Outbox properties;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate transactionTemplate, ChassisProperties properties,
                       MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties.outbox();
        this.meterRegistry = meterRegistry;
        Gauge.builder("outbox.pending.events", repository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Eventos aguardando publicação no Kafka")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${chassis.outbox.poll-interval:500ms}")
    public void publishPending() {
        int published;
        do {
            published = publishBatch();
        } while (published == properties.batchSize());
    }

    /** @return quantos eventos foram publicados com sucesso neste lote */
    int publishBatch() {
        Integer count = transactionTemplate.execute(status -> {
            List<OutboxEventEntity> batch = repository.lockNextPending(properties.batchSize());
            int ok = 0;
            for (OutboxEventEntity event : batch) {
                if (!send(event)) {
                    break;
                }
                ok++;
            }
            return ok;
        });
        return count == null ? 0 : count;
    }

    private boolean send(OutboxEventEntity event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getMessageKey(),
                event.getPayload());
        record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        if (event.getCorrelationId() != null) {
            record.headers().add(CorrelationId.HEADER, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            event.markPublished();
            Counter.builder("outbox.events.published").tag("type", event.getEventType())
                    .description("Eventos publicados a partir do outbox").register(meterRegistry).increment();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.registerFailure("interrompido");
            return false;
        } catch (Exception e) {
            log.warn("Falha ao publicar evento {} ({}) no tópico {}: {}", event.getId(), event.getEventType(),
                    event.getTopic(), e.getMessage());
            event.registerFailure(e.getMessage());
            return false;
        }
    }
}
