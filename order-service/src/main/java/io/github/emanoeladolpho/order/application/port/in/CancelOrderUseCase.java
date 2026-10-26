package io.github.emanoeladolpho.order.application.port.in;

import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;

public interface CancelOrderUseCase {

    Order cancel(OrderId orderId, Requester requester, String reason);
}
