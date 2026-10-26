package io.github.emanoeladolpho.payment.domain.event;

import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentApproved(
        UUID eventId,
        Instant occurredAt,
        String paymentId,
        String orderId,
        Money amount,
        String gatewayTransactionId
) implements DomainEvent {

    /** A chave de partição é o pedido: mantém a ordem dos eventos de um mesmo pedido. */
    @Override
    public String aggregateId() {
        return orderId;
    }
}
