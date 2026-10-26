package io.github.emanoeladolpho.order.application.port.out;

import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PageResult;
import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId id);

    PageResult<Order> findByCustomer(CustomerId customerId, PageQuery page);

    PageResult<Order> findAll(PageQuery page);
}
