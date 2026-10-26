package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

import io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import org.springframework.stereotype.Component;

/** Traduz a API do provedor para a porta PaymentGateway e as falhas técnicas para a exceção da aplicação. */
@Component
class PaymentGatewayAdapter implements PaymentGateway {

    private final SimulatedGatewayClient client;

    PaymentGatewayAdapter(SimulatedGatewayClient client) {
        this.client = client;
    }

    @Override
    public Result charge(PaymentId paymentId, OrderId orderId, Money amount) {
        try {
            return toResult(client.charge(paymentId.toString(), amount.amount()));
        } catch (GatewayTechnicalException e) {
            throw new PaymentGatewayUnavailableException("Gateway de pagamento indisponível", e);
        }
    }

    @Override
    public Result refund(PaymentId paymentId, String transactionId, Money amount) {
        try {
            return toResult(client.refund(transactionId, amount.amount()));
        } catch (GatewayTechnicalException e) {
            throw new PaymentGatewayUnavailableException("Gateway de pagamento indisponível", e);
        }
    }

    private static Result toResult(SimulatedGatewayClient.ChargeResponse response) {
        return response.approved()
                ? new Result.Approved(response.transactionId())
                : new Result.Declined(response.declineReason());
    }
}
