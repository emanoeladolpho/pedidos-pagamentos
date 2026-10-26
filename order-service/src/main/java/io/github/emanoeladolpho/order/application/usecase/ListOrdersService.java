package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.port.in.ListOrdersUseCase;
import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PageResult;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.domain.model.Order;

public class ListOrdersService implements ListOrdersUseCase {

    private final OrderRepository orderRepository;

    public ListOrdersService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public PageResult<Order> list(Requester requester, PageQuery page) {
        return requester.admin()
                ? orderRepository.findAll(page)
                : orderRepository.findByCustomer(requester.customerId(), page);
    }
}
