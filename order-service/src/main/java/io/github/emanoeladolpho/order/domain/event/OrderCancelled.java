package io.github.emanoeladolpho.order.domain.event;

import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import io.github.emanoeladolpho.order.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Emitido no cancelamento. Se o pagamento já tinha sido aprovado, o payment-service fará o estorno. */
public record OrderCancelled(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        OrderStatus previousStatus,
        String reason
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return orderId;
    }
}
