package io.github.emanoeladolpho.order.domain;

import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.model.Quantity;
import io.github.emanoeladolpho.order.domain.model.Sku;

import java.util.List;

public final class OrderFixtures {

    public static final CustomerId CUSTOMER = new CustomerId("customer-1");

    private OrderFixtures() {
    }

    public static OrderItem item(String sku, String price, int quantity) {
        return new OrderItem(new Sku(sku), "Produto " + sku, Money.of(price), new Quantity(quantity));
    }

    /** Pedido recém-criado, já sem os eventos pendentes. */
    public static Order placedOrder() {
        Order order = Order.place(CUSTOMER, List.of(item("SKU-1", "10.00", 2), item("SKU-2", "5.50", 1)));
        order.pullDomainEvents();
        return order;
    }
}
