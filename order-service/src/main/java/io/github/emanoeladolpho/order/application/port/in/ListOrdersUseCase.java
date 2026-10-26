package io.github.emanoeladolpho.order.application.port.in;

import io.github.emanoeladolpho.order.domain.model.Order;

public interface ListOrdersUseCase {

    /** Clientes veem apenas os próprios pedidos; administradores veem todos. */
    PageResult<Order> list(Requester requester, PageQuery page);
}
