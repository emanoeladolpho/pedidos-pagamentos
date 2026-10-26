package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.order.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;

/**
 * Consumidor idempotente: o registro do evento processado e a mudança no pedido acontecem
 * na mesma transação. Uma reentrega do mesmo evento é simplesmente ignorada.
 */
public class ApplyPaymentResultService implements ApplyPaymentResultUseCase {

    static final String CONSUMER = "order-service.payment-result";

    private final OrderRepository orderRepository;
    private final ProcessedEventStore processedEvents;
    private final UnitOfWork unitOfWork;

    public ApplyPaymentResultService(OrderRepository orderRepository, ProcessedEventStore processedEvents,
                                     UnitOfWork unitOfWork) {
        this.orderRepository = orderRepository;
        this.processedEvents = processedEvents;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public void apply(Command command) {
        unitOfWork.run(() -> {
            if (processedEvents.isProcessed(command.eventId(), CONSUMER)) {
                return;
            }
            OrderId orderId = OrderId.of(command.orderId());
            Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
            switch (command.outcome()) {
                case INITIATED -> order.markPaymentPending();
                case APPROVED -> order.markPaid();
                case DECLINED -> order.markPaymentFailed(command.reason());
                case REFUNDED -> order.markRefunded(command.reason());
            }
            orderRepository.save(order);
            processedEvents.markProcessed(command.eventId(), CONSUMER);
        });
    }
}
