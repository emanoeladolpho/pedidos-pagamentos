package io.github.emanoeladolpho.payment.application.usecase;

import io.github.emanoeladolpho.payment.application.exception.PaymentNotFoundException;
import io.github.emanoeladolpho.payment.application.port.in.GetPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.Requester;
import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;

import java.util.Optional;

public class GetPaymentService implements GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Payment get(PaymentId paymentId, Requester requester) {
        return visible(paymentRepository.findById(paymentId), requester)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Override
    public Payment getByOrder(OrderId orderId, Requester requester) {
        return visible(paymentRepository.findByOrderId(orderId), requester)
                .orElseThrow(() -> new PaymentNotFoundException("pedido " + orderId));
    }

    private Optional<Payment> visible(Optional<Payment> payment, Requester requester) {
        return payment.filter(p -> requester.admin() || p.belongsTo(requester.customerId()));
    }
}
