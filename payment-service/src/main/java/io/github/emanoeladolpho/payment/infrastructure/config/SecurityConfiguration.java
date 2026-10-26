package io.github.emanoeladolpho.payment.infrastructure.config;

import io.github.emanoeladolpho.chassis.security.KeycloakJwtConverter;
import io.github.emanoeladolpho.chassis.security.ProblemDetailsSecurityHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Resource server stateless: valida o JWT emitido pelo Keycloak e exige a role USER ou ADMIN (estorno manual: só ADMIN). */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    static final String[] PUBLIC_PATHS = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KeycloakJwtConverter jwtConverter,
                                            ProblemDetailsSecurityHandler problemHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/*/refund").hasRole(KeycloakJwtConverter.ADMIN)
                        .anyRequest().hasAnyRole(KeycloakJwtConverter.USER, KeycloakJwtConverter.ADMIN))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
                .build();
    }
}
