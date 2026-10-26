package io.github.emanoeladolpho.chassis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "chassis")
public record ChassisProperties(
        @DefaultValue Outbox outbox,
        @DefaultValue Consumer consumer,
        @DefaultValue Security security
) {

    /** @param pollInterval intervalo entre varreduras do outbox; @param batchSize eventos por varredura */
    public record Outbox(@DefaultValue("500ms") Duration pollInterval, @DefaultValue("100") int batchSize,
                         @DefaultValue("10s") Duration sendTimeout) {
    }

    public record Consumer(@DefaultValue("3") int maxRetries, @DefaultValue("500ms") Duration initialBackoff,
                           @DefaultValue("2.0") double backoffMultiplier) {
    }

    /**
     * @param jwkSetUri endereço das chaves públicas do provedor de identidade (Keycloak)
     * @param issuer    valor esperado na claim "iss" do token
     */
    public record Security(String jwkSetUri, String issuer) {
    }
}
