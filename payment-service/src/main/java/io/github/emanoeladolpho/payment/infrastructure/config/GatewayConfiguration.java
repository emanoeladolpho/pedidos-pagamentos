package io.github.emanoeladolpho.payment.infrastructure.config;

import io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway.SimulatedGatewayClient;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway.SimulatedGatewayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimulatedGatewayProperties.class)
class GatewayConfiguration {

    @Bean
    SimulatedGatewayClient simulatedGatewayClient(SimulatedGatewayProperties properties) {
        return new SimulatedGatewayClient(properties);
    }
}
