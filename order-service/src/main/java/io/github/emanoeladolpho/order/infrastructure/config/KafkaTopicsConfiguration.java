package io.github.emanoeladolpho.order.infrastructure.config;

import io.github.emanoeladolpho.chassis.messaging.DeadLetterTopics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Declara, com a mesma especificação nos dois serviços, todos os tópicos que este serviço usa: o que publica,
 * o que consome e a DLT do que consome. Assim, qualquer que seja a ordem de inicialização, o tópico nasce com o
 * número correto de partições (o consumidor nunca o cria com o padrão do broker, ver allow.auto.create.topics).
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfiguration {

    @Bean
    KafkaAdmin.NewTopics orderServiceTopics(@Value("${app.kafka.topics.order-events}") String orderEvents,
                                            @Value("${app.kafka.topics.payment-events}") String paymentEvents,
                                            @Value("${app.kafka.partitions:3}") int partitions) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(orderEvents).partitions(partitions).build(),
                TopicBuilder.name(paymentEvents).partitions(partitions).build(),
                TopicBuilder.name(DeadLetterTopics.of(paymentEvents)).partitions(partitions).build());
    }
}
