package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Comportamento do gateway simulado.
 *
 * @param latency     tempo de resposta simulado
 * @param failureRate probabilidade (0 a 1) de falha técnica aleatória (timeout/erro 5xx)
 * @param declineRate probabilidade (0 a 1) de recusa aleatória
 */
@ConfigurationProperties(prefix = "app.gateway.simulated")
public record SimulatedGatewayProperties(
        @DefaultValue("50ms") Duration latency,
        @DefaultValue("0") double failureRate,
        @DefaultValue("0") double declineRate
) {
}
