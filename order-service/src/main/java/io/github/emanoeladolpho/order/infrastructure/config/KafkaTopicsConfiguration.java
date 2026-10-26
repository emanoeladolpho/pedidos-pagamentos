package io.github.emanoeladolpho.order.infrastructure.config;

import io.github.emanoeladolpho.chassis.messaging.DeadLetterTopics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** Tópicos de que este serviço é dono: o que ele publica e a DLT do que ele consome. */
@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfiguration {

    @Bean
    KafkaAdmin.NewTopics orderServiceTopics(@Value("${app.kafka.topics.order-events}") String orderEvents,
                                            @Value("${app.kafka.topics.payment-events}") String paymentEvents,
                                            @Value("${app.kafka.partitions:3}") int partitions) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(orderEvents).partitions(partitions).build(),
                TopicBuilder.name(DeadLetterTopics.of(paymentEvents)).partitions(partitions).build());
    }
}
