package io.github.emanoeladolpho.payment.infrastructure.config;

import io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway.PaymentGatewayAdapter;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway.SimulatedGatewayClient;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway.SimulatedGatewayProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Monta o adapter do gateway com as políticas de resiliência configuradas em resilience4j.*.instances. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimulatedGatewayProperties.class)
class GatewayConfiguration {

    @Bean
    SimulatedGatewayClient simulatedGatewayClient(SimulatedGatewayProperties properties) {
        return new SimulatedGatewayClient(properties);
    }

    /** Threads virtuais: chamadas de I/O bloqueante que o TimeLimiter pode abandonar sem esgotar um pool. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService gatewayExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    PaymentGatewayAdapter paymentGatewayAdapter(SimulatedGatewayClient client, ExecutorService gatewayExecutor,
                                                TimeLimiterRegistry timeLimiters,
                                                CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
        return new PaymentGatewayAdapter(client, gatewayExecutor, timeLimiters, circuitBreakers, retries);
    }
}
