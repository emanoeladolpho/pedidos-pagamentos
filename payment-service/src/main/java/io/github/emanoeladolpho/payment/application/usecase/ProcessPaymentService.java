package io.github.emanoeladolpho.payment.application.usecase;

import io.github.emanoeladolpho.payment.application.port.in.ProcessPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.payment.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;

import java.util.Optional;

/**
 * Processa o pagamento em três passos, para nunca manter uma transação aberta durante a chamada externa:
 * <ol>
 *   <li>transação 1: cria o pagamento PENDING (uma vez por pedido) e publica PaymentInitiated;</li>
 *   <li>chamada ao gateway, fora de transação;</li>
 *   <li>transação 2: registra o resultado, publica Approved/Declined e marca o evento como processado.</li>
 * </ol>
 * Se o gateway falhar, a exceção sobe, a mensagem é reentregue e o fluxo recomeça a partir do
 * pagamento PENDING já existente (o evento só é marcado como processado no final).
 */
public class ProcessPaymentService implements ProcessPaymentUseCase {

    static final String CONSUMER = "payment-service.order-created";

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final DomainEventPublisher eventPublisher;
    private final ProcessedEventStore processedEvents;
    private final UnitOfWork unitOfWork;

    public ProcessPaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                                 DomainEventPublisher eventPublisher, ProcessedEventStore processedEvents,
                                 UnitOfWork unitOfWork) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.processedEvents = processedEvents;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public void process(Command command) {
        Optional<Payment> pending = unitOfWork.execute(() -> preparePayment(command));
        if (pending.isEmpty()) {
            return;
        }
        Payment payment = pending.get();
        PaymentGateway.Result result = paymentGateway.charge(payment.getId(), payment.getOrderId(), payment.getAmount());

        unitOfWork.run(() -> {
            Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
            if (current.isPending()) {
                switch (result) {
                    case PaymentGateway.Result.Approved approved -> current.approve(approved.transactionId());
                    case PaymentGateway.Result.Declined declined -> current.decline(declined.reason());
                }
                paymentRepository.save(current);
                eventPublisher.publish(current.pullDomainEvents());
            }
            processedEvents.markProcessed(command.eventId(), CONSUMER);
        });
    }

    /** Devolve o pagamento a ser cobrado, ou vazio se não há nada a fazer (evento repetido ou já finalizado). */
    private Optional<Payment> preparePayment(Command command) {
        if (processedEvents.isProcessed(command.eventId(), CONSUMER)) {
            return Optional.empty();
        }
        OrderId orderId = OrderId.of(command.orderId());
        Payment payment = paymentRepository.findByOrderId(orderId).orElseGet(() -> {
            Payment created = Payment.initiate(orderId, new CustomerId(command.customerId()),
                    new Money(command.amount(), command.currency()));
            paymentRepository.save(created);
            eventPublisher.publish(created.pullDomainEvents());
            return created;
        });
        if (!payment.isPending()) {
            processedEvents.markProcessed(command.eventId(), CONSUMER);
            return Optional.empty();
        }
        return Optional.of(payment);
    }
}
