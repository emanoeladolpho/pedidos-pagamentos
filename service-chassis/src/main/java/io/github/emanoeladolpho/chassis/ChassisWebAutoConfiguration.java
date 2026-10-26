package io.github.emanoeladolpho.chassis;

import io.github.emanoeladolpho.chassis.correlation.CorrelationIdFilter;
import io.github.emanoeladolpho.chassis.security.KeycloakJwtConverter;
import io.github.emanoeladolpho.chassis.security.ProblemDetailsSecurityHandler;
import io.github.emanoeladolpho.chassis.web.BaseExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Microservice Chassis, parte web: correlation id nas requisições HTTP, erros no formato
 * Problem Details e validação de tokens JWT do Keycloak. Também é carregada nos testes
 * {@code @WebMvcTest} (ver META-INF/spring/...AutoConfigureWebMvc.imports).
 */
@AutoConfiguration
@EnableConfigurationProperties(ChassisProperties.class)
@Import(BaseExceptionHandler.class)
public class ChassisWebAutoConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
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
