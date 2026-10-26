package io.github.emanoeladolpho.chassis;

import io.github.emanoeladolpho.chassis.correlation.CorrelationIdRecordInterceptor;
import io.github.emanoeladolpho.chassis.inbox.Inbox;
import io.github.emanoeladolpho.chassis.inbox.ProcessedEventRepository;
import io.github.emanoeladolpho.chassis.messaging.KafkaErrorHandlingConfiguration;
import io.github.emanoeladolpho.chassis.outbox.OutboxEventRepository;
import io.github.emanoeladolpho.chassis.outbox.OutboxRelay;
import io.github.emanoeladolpho.chassis.outbox.OutboxWriter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Microservice Chassis, parte de mensageria: Transactional Outbox, inbox de eventos processados,
 * tratamento de erros dos consumidores (retry + DLT) e propagação do correlation id pelo Kafka.
 * Registrada como auto-configuração; os serviços só adicionam a dependência.
 */
@AutoConfiguration(before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@AutoConfigurationPackage
@EnableConfigurationProperties(ChassisProperties.class)
@EnableScheduling
@Import(KafkaErrorHandlingConfiguration.class)
public class ChassisMessagingAutoConfiguration {

    @Bean
    RecordInterceptor<Object, Object> correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }

    @Bean
    OutboxWriter outboxWriter(OutboxEventRepository repository) {
        return new OutboxWriter(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "chassis.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                            TransactionTemplate transactionTemplate, ChassisProperties properties,
                            MeterRegistry meterRegistry) {
        return new OutboxRelay(repository, kafkaTemplate, transactionTemplate, properties, meterRegistry);
    }

    @Bean
    Inbox inbox(ProcessedEventRepository repository) {
        return new Inbox(repository);
    }
}
