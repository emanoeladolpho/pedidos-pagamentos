package io.github.emanoeladolpho.payment.application.usecase;

import io.github.emanoeladolpho.payment.application.exception.PaymentNotFoundException;
import io.github.emanoeladolpho.payment.application.exception.RefundRejectedException;
import io.github.emanoeladolpho.payment.application.port.in.RefundPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;
import io.github.emanoeladolpho.payment.domain.shared.DomainException;

public class RefundPaymentService implements RefundPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final DomainEventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public RefundPaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                                DomainEventPublisher eventPublisher, UnitOfWork unitOfWork) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Payment refund(PaymentId paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new DomainException("PAYMENT_NOT_REFUNDABLE",
                    "Só é possível estornar pagamentos aprovados (status atual: %s)".formatted(payment.getStatus()));
        }
        PaymentGateway.Result result = paymentGateway.refund(paymentId, payment.getGatewayTransactionId(),
                payment.getAmount());
        if (result instanceof PaymentGateway.Result.Declined declined) {
            throw new RefundRejectedException(declined.reason());
        }
        return unitOfWork.execute(() -> {
            Payment current = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
            current.refund(reason);
            paymentRepository.save(current);
            eventPublisher.publish(current.pullDomainEvents());
            return current;
        });
    }
}
