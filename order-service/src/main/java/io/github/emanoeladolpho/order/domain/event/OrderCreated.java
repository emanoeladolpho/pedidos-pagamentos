package io.github.emanoeladolpho.order.domain.event;

import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        List<OrderItem> items,
        Money total
) implements DomainEvent {

    public OrderCreated {
        items = List.copyOf(items);
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
