package io.github.emanoeladolpho.chassis;

import io.github.emanoeladolpho.chassis.correlation.CorrelationIdFilter;
import io.github.emanoeladolpho.chassis.correlation.CorrelationIdRecordInterceptor;
import io.github.emanoeladolpho.chassis.inbox.Inbox;
import io.github.emanoeladolpho.chassis.inbox.ProcessedEventRepository;
import io.github.emanoeladolpho.chassis.messaging.KafkaErrorHandlingConfiguration;
import io.github.emanoeladolpho.chassis.outbox.OutboxEventRepository;
import io.github.emanoeladolpho.chassis.outbox.OutboxRelay;
import io.github.emanoeladolpho.chassis.outbox.OutboxWriter;
import io.github.emanoeladolpho.chassis.security.KeycloakJwtConverter;
import io.github.emanoeladolpho.chassis.security.ProblemDetailsSecurityHandler;
import io.github.emanoeladolpho.chassis.web.BaseExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Microservice Chassis: infraestrutura transversal que todo serviço precisa e que não pertence
 * a nenhum domínio. Registrada como auto-configuração; os serviços só adicionam a dependência.
 */
@AutoConfiguration(before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@AutoConfigurationPackage
@EnableConfigurationProperties(ChassisProperties.class)
@EnableScheduling
@Import({KafkaErrorHandlingConfiguration.class, BaseExceptionHandler.class})
public class ChassisAutoConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

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

    @Bean
    ProblemDetailsSecurityHandler problemDetailsSecurityHandler(ObjectMapper objectMapper) {
        return new ProblemDetailsSecurityHandler(objectMapper);
    }

    @Bean
    KeycloakJwtConverter keycloakJwtConverter() {
        return new KeycloakJwtConverter();
    }

    /**
     * As chaves públicas são buscadas no endereço interno do Keycloak (jwk-set-uri), mas o "iss" validado
     * é o endereço público pelo qual o cliente obteve o token — no docker-compose eles são diferentes.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "chassis.security.jwk-set-uri")
    JwtDecoder jwtDecoder(ChassisProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.security().jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(properties.security().issuer() == null
                ? JwtValidators.createDefault()
                : JwtValidators.createDefaultWithIssuer(properties.security().issuer()));
        return decoder;
    }
}
