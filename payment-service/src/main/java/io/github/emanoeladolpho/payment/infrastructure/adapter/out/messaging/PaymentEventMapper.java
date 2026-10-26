package io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging;

import io.github.emanoeladolpho.payment.domain.event.PaymentApproved;
import io.github.emanoeladolpho.payment.domain.event.PaymentDeclined;
import io.github.emanoeladolpho.payment.domain.event.PaymentInitiated;
import io.github.emanoeladolpho.payment.domain.event.PaymentRefunded;
import io.github.emanoeladolpho.payment.domain.shared.DomainEvent;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging.PaymentEventMessages.PaymentApprovedV1;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging.PaymentEventMessages.PaymentDeclinedV1;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging.PaymentEventMessages.PaymentInitiatedV1;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging.PaymentEventMessages.PaymentRefundedV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.emanoeladolpho.chassis.messaging.EventEnvelope;
import io.github.emanoeladolpho.chassis.outbox.OutboxMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventMapper {

    private final ObjectMapper objectMapper;
    private final String topic;

    public PaymentEventMapper(ObjectMapper objectMapper, @Value("${app.kafka.topics.payment-events}") String topic) {
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public OutboxMessage toMessage(DomainEvent event) {
        Object data = switch (event) {
            case PaymentInitiated e -> new PaymentInitiatedV1(e.paymentId(), e.orderId(), e.amount().amount(),
                    e.amount().currency());
            case PaymentApproved e -> new PaymentApprovedV1(e.paymentId(), e.orderId(), e.amount().amount(),
                    e.amount().currency(), e.gatewayTransactionId());
            case PaymentDeclined e -> new PaymentDeclinedV1(e.paymentId(), e.orderId(), e.reason());
            case PaymentRefunded e -> new PaymentRefundedV1(e.paymentId(), e.orderId(), e.amount().amount(),
                    e.amount().currency(), e.reason());
            default -> throw new IllegalArgumentException("Evento sem mapeamento: " + event.getClass().getName());
        };
        String eventType = event.getClass().getSimpleName();
        var envelope = new EventEnvelope<>(event.eventId(), eventType, PaymentEventMessages.VERSION,
                event.occurredAt(), data);
        try {
            return new OutboxMessage(event.eventId(), eventType, topic, event.aggregateId(),
                    objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento", e);
        }
    }
}
