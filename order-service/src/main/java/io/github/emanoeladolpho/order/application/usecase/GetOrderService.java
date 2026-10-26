package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.application.port.in.GetOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;

public class GetOrderService implements GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Pedido de outro cliente é tratado como inexistente, para não revelar quais ids existem. */
    @Override
    public Order get(OrderId orderId, Requester requester) {
        return orderRepository.findById(orderId)
                .filter(order -> requester.admin() || order.isOwnedBy(requester.customerId()))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
