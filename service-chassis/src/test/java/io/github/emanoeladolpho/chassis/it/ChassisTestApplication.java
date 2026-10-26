package io.github.emanoeladolpho.chassis.it;

import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import io.github.emanoeladolpho.chassis.messaging.DeadLetterTopics;
import io.github.emanoeladolpho.chassis.messaging.MalformedEventException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Aplicação mínima que usa o chassis como um serviço real usaria. */
@SpringBootApplication
public class ChassisTestApplication {

    static final String TOPIC = "chassis-test";

    @Bean
    KafkaAdmin.NewTopics chassisTestTopics() {
        return new KafkaAdmin.NewTopics(TopicBuilder.name(TOPIC).partitions(1).build(),
                TopicBuilder.name(DeadLetterTopics.of(TOPIC)).partitions(1).build());
    }

    /** Consumidor de teste: registra o que recebeu (com o correlation id do MDC) ou falha sob comando. */
    @Component
    static class RecordingListener {

        final List<String> received = new CopyOnWriteArrayList<>();
        final AtomicInteger transientFailures = new AtomicInteger();

        @KafkaListener(topics = TOPIC, groupId = "chassis-test")
        void on(String message) {
            switch (message) {
                case "malformed" -> throw new MalformedEventException("payload inválido", null);
                case "transient" -> {
                    transientFailures.incrementAndGet();
                    throw new IllegalStateException("falha temporária");
                }
                default -> received.add(message + "|" + CorrelationId.current().orElse("-"));
            }
        }
    }
}
