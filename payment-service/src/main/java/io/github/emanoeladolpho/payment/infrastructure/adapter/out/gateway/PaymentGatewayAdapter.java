package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

import io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Traduz a API do provedor para a porta PaymentGateway, protegendo cada chamada com Resilience4j:
 * <pre>retry( circuitBreaker( timeLimiter( chamada ) ) )</pre>
 * Cada tentativa conta para o circuit breaker; com o circuito aberto a chamada falha na hora (sem retry).
 * O retry é seguro porque o provedor é idempotente pela chave enviada (id do pagamento).
 * Falhas técnicas viram {@link PaymentGatewayUnavailableException}; recusas são um resultado normal.
 */
public class PaymentGatewayAdapter implements PaymentGateway {

    public static final String INSTANCE = "payment-gateway";

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayAdapter.class);

    private final SimulatedGatewayClient client;
    private final ExecutorService executor;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public PaymentGatewayAdapter(SimulatedGatewayClient client, ExecutorService executor,
                                 TimeLimiterRegistry timeLimiters, CircuitBreakerRegistry circuitBreakers,
                                 RetryRegistry retries) {
        this.client = client;
        this.executor = executor;
        this.timeLimiter = timeLimiters.timeLimiter(INSTANCE);
        this.circuitBreaker = circuitBreakers.circuitBreaker(INSTANCE);
        this.retry = retries.retry(INSTANCE);
        this.retry.getEventPublisher().onRetry(event -> log.warn("Gateway: tentativa {} falhou ({}), repetindo",
                event.getNumberOfRetryAttempts(), event.getLastThrowable().toString()));
    }

    @Override
    public Result charge(PaymentId paymentId, OrderId orderId, Money amount) {
        return call(() -> toResult(client.charge(paymentId.toString(), amount.amount())));
    }

    @Override
    public Result refund(PaymentId paymentId, String transactionId, Money amount) {
        return call(() -> toResult(client.refund(transactionId, amount.amount())));
    }

    private Result call(Callable<Result> operation) {
        Callable<Result> withTimeout = () -> timeLimiter.executeFutureSupplier(() -> executor.submit(operation));
        try {
            return Retry.decorateCallable(retry, CircuitBreaker.decorateCallable(circuitBreaker, withTimeout)).call();
        } catch (CallNotPermittedException e) {
            throw new PaymentGatewayUnavailableException("Circuit breaker aberto para o gateway de pagamento", e);
        } catch (GatewayTechnicalException | TimeoutException e) {
            throw new PaymentGatewayUnavailableException("Gateway de pagamento indisponível", e);
        } catch (Exception e) {
            throw new PaymentGatewayUnavailableException("Falha inesperada ao chamar o gateway de pagamento", e);
        }
    }

    private static Result toResult(SimulatedGatewayClient.ChargeResponse response) {
        return response.approved()
                ? new Result.Approved(response.transactionId())
                : new Result.Declined(response.declineReason());
    }
}
