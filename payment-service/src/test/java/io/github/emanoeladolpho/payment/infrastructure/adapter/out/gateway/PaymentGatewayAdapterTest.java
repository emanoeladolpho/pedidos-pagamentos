package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

import io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Políticas de resiliência com as mesmas regras do payment-service.yml (valores de tempo reduzidos). */
class PaymentGatewayAdapterTest {

    private static final Money AMOUNT = new Money(new BigDecimal("100.00"), "BRL");
    private static final SimulatedGatewayClient.ChargeResponse APPROVED =
            new SimulatedGatewayClient.ChargeResponse(true, "gw-1", null);

    private final SimulatedGatewayClient client = mock(SimulatedGatewayClient.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .recordExceptions(GatewayTechnicalException.class, TimeoutException.class)
            .build());
    private final PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(client, executor,
            TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(200)).build()),
            circuitBreakers,
            RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10))
                    .retryExceptions(GatewayTechnicalException.class, TimeoutException.class).build()));

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private PaymentGateway.Result charge() {
        return adapter.charge(PaymentId.newId(), OrderId.of(UUID.randomUUID().toString()), AMOUNT);
    }

    @Test
    void transientFailureShouldBeRetried() {
        when(client.charge(anyString(), any()))
                .thenThrow(new GatewayTechnicalException("503"))
                .thenReturn(APPROVED);

        assertThat(charge()).isEqualTo(new PaymentGateway.Result.Approved("gw-1"));
        verify(client, times(2)).charge(anyString(), any());
    }

    @Test
    void declineIsAResultAndShouldNotBeRetried() {
        when(client.charge(anyString(), any()))
                .thenReturn(new SimulatedGatewayClient.ChargeResponse(false, null, "Saldo insuficiente"));

        assertThat(charge()).isEqualTo(new PaymentGateway.Result.Declined("Saldo insuficiente"));
        verify(client, times(1)).charge(anyString(), any());
    }

    @Test
    void persistentFailureShouldBecomeGatewayUnavailableAfterAllAttempts() {
        when(client.charge(anyString(), any())).thenThrow(new GatewayTechnicalException("503"));

        assertThatThrownBy(this::charge)
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasCauseInstanceOf(GatewayTechnicalException.class);
        verify(client, times(3)).charge(anyString(), any());
    }

    @Test
    void slowCallShouldTimeOut() {
        when(client.charge(anyString(), any())).thenAnswer(invocation -> {
            Thread.sleep(2_000);
            return APPROVED;
        });

        assertThatThrownBy(this::charge)
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void openCircuitShouldFailFastWithoutCallingTheProvider() {
        when(client.charge(anyString(), any())).thenThrow(new GatewayTechnicalException("503"));
        assertThatThrownBy(this::charge).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThatThrownBy(this::charge).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(circuitBreakers.circuitBreaker(PaymentGatewayAdapter.INSTANCE).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        SimulatedGatewayClient other = mock(SimulatedGatewayClient.class);
        var sameCircuit = new PaymentGatewayAdapter(other, executor, TimeLimiterRegistry.ofDefaults(),
                circuitBreakers, RetryRegistry.ofDefaults());

        assertThatThrownBy(() -> sameCircuit.refund(PaymentId.newId(), "gw-1", AMOUNT))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        verifyNoInteractions(other);
    }

    @Test
    void refundShouldMapProviderResponse() {
        when(client.refund("gw-1", AMOUNT.amount())).thenReturn(new SimulatedGatewayClient.ChargeResponse(true,
                "rf-1", null));

        assertThat(adapter.refund(PaymentId.newId(), "gw-1", AMOUNT))
                .isEqualTo(new PaymentGateway.Result.Approved("rf-1"));
    }
}
