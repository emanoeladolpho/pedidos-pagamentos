package io.github.emanoeladolpho.chassis.messaging;

import io.github.emanoeladolpho.chassis.ChassisProperties;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Política de falha dos consumidores: tenta novamente com backoff exponencial e, esgotadas as
 * tentativas, publica a mensagem original em {@code <tópico>-dlt} (mesma partição), com headers
 * contendo a exceção. Mensagens malformadas vão direto para a DLT.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaErrorHandlingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfiguration.class);

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations, ChassisProperties properties) {
        // Mesma partição da mensagem original: preserva a ordem por chave também na DLT
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(DeadLetterTopics.of(record.topic()), record.partition()));
        ChassisProperties.Consumer retry = properties.consumer();
        var backOff = new ExponentialBackOff(retry.initialBackoff().toMillis(), retry.backoffMultiplier());
        backOff.setMaxAttempts(retry.maxRetries());
        var handler = new DefaultErrorHandler((record, exception) -> {
            log.error("Mensagem enviada para a DLT após falhas: topic={} partition={} offset={} key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), exception);
            recoverer.accept(record, exception);
        }, backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }
}
