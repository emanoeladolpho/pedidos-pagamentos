package io.github.emanoeladolpho.order.infrastructure;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

/** Utilitários para publicar e ler mensagens nos testes de integração. */
public final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    public static void send(KafkaTemplate<String, String> template, String topic, String key, String payload,
                            String correlationId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        if (correlationId != null) {
            record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
        }
        template.send(record).get();
    }

    /** Lê o tópico desde o início até encontrar uma mensagem que satisfaça o filtro. */
    public static ConsumerRecord<String, String> awaitRecord(ConsumerFactory<String, String> factory, String topic,
                                                             Predicate<ConsumerRecord<String, String>> filter) {
        try (Consumer<String, String> consumer = factory.createConsumer("test-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (filter.test(record)) {
                        found.add(record);
                    }
                });
                return !found.isEmpty();
            });
            return found.getFirst();
        }
    }

    public static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
