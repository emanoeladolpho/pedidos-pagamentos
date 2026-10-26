package io.github.emanoeladolpho.payment.application.usecase;

import io.github.emanoeladolpho.payment.application.exception.RefundRejectedException;
import io.github.emanoeladolpho.payment.application.port.in.HandleOrderCancelledUseCase;
import io.github.emanoeladolpho.payment.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.payment.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;

import java.util.Optional;

/**
 * Pedido cancelado: pagamento pendente é cancelado (não será cobrado); pagamento aprovado é estornado.
 * Como os eventos do mesmo pedido estão na mesma partição, o OrderCreated sempre é processado antes.
 */
public class HandleOrderCancelledService implements HandleOrderCancelledUseCase {

    static final String CONSUMER = "payment-service.order-cancelled";

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final DomainEventPublisher eventPublisher;
    private final ProcessedEventStore processedEvents;
    private final UnitOfWork unitOfWork;

    public HandleOrderCancelledService(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                                       DomainEventPublisher eventPublisher, ProcessedEventStore processedEvents,
                                       UnitOfWork unitOfWork) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.processedEvents = processedEvents;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public void handle(Command command) {
        Optional<Payment> toRefund = unitOfWork.execute(() -> {
            if (processedEvents.isProcessed(command.eventId(), CONSUMER)) {
                return Optional.<Payment>empty();
            }
            Optional<Payment> payment = paymentRepository.findByOrderId(OrderId.of(command.orderId()));
            if (payment.isPresent() && payment.get().getStatus() == PaymentStatus.APPROVED) {
                return payment;
            }
            payment.filter(Payment::isPending).ifPresent(pending -> {
                pending.cancel();
                paymentRepository.save(pending);
            });
            processedEvents.markProcessed(command.eventId(), CONSUMER);
            return Optional.<Payment>empty();
        });
        if (toRefund.isEmpty()) {
            return;
        }

        Payment payment = toRefund.get();
        PaymentGateway.Result result = paymentGateway.refund(payment.getId(), payment.getGatewayTransactionId(),
                payment.getAmount());
        if (result instanceof PaymentGateway.Result.Declined declined) {
            throw new RefundRejectedException(declined.reason());
        }
        unitOfWork.run(() -> {
            Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
            if (current.getStatus() == PaymentStatus.APPROVED) {
                current.refund(command.reason() == null ? "Pedido cancelado" : command.reason());
                paymentRepository.save(current);
                eventPublisher.publish(current.pullDomainEvents());
            }
            processedEvents.markProcessed(command.eventId(), CONSUMER);
        });
    }
}
