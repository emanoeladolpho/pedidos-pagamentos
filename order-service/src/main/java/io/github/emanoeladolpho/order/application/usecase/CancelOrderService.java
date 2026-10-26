package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.port.in.CancelOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.GetOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;

/** Cancela o pedido e publica OrderCancelled; se já estava pago, o payment-service faz o estorno. */
public class CancelOrderService implements CancelOrderUseCase {

    private final GetOrderUseCase getOrder;
    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public CancelOrderService(GetOrderUseCase getOrder, OrderRepository orderRepository,
                              DomainEventPublisher eventPublisher, UnitOfWork unitOfWork) {
        this.getOrder = getOrder;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Order cancel(OrderId orderId, Requester requester, String reason) {
        return unitOfWork.execute(() -> {
            Order order = getOrder.get(orderId, requester);
            order.cancel(reason);
            orderRepository.save(order);
            eventPublisher.publish(order.pullDomainEvents());
            return order;
        });
    }
}
