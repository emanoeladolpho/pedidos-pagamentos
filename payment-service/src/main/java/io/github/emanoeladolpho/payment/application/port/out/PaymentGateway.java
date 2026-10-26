package io.github.emanoeladolpho.payment.application.port.out;

import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;

/**
 * Gateway de pagamento externo. Recusas de negócio voltam como {@link Result.Declined};
 * falhas técnicas (timeout, indisponibilidade) lançam {@link io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException}.
 * O {@code paymentId} é enviado como chave de idempotência ao provedor.
 */
public interface PaymentGateway {

    Result charge(PaymentId paymentId, OrderId orderId, Money amount);

    Result refund(PaymentId paymentId, String transactionId, Money amount);

    sealed interface Result permits Result.Approved, Result.Declined {

        record Approved(String transactionId) implements Result {
        }

        record Declined(String reason) implements Result {
        }
    }
}
