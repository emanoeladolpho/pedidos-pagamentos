package io.github.emanoeladolpho.order.infrastructure.adapter.out.messaging;

import io.github.emanoeladolpho.order.domain.event.OrderCancelled;
import io.github.emanoeladolpho.order.domain.event.OrderCreated;
import io.github.emanoeladolpho.order.domain.shared.DomainEvent;
import io.github.emanoeladolpho.order.infrastructure.adapter.out.messaging.OrderEventMessages.OrderCancelledV1;
import io.github.emanoeladolpho.order.infrastructure.adapter.out.messaging.OrderEventMessages.OrderCreatedV1;
import io.github.emanoeladolpho.chassis.messaging.EventEnvelope;
import io.github.emanoeladolpho.chassis.outbox.OutboxMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Traduz eventos de domínio para mensagens de integração (envelope JSON + tópico + chave). */
@Component
public class OrderEventMapper {

    private final ObjectMapper objectMapper;
    private final String topic;

    public OrderEventMapper(ObjectMapper objectMapper, @Value("${app.kafka.topics.order-events}") String topic) {
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public OutboxMessage toMessage(DomainEvent event) {
        Object data = switch (event) {
            case OrderCreated created -> new OrderCreatedV1(created.orderId(), created.customerId(),
                    created.total().amount(), created.total().currency(),
                    created.items().stream().map(item -> new OrderCreatedV1.Item(item.sku().value(),
                            item.productName(), item.unitPrice().amount(), item.quantity().value())).toList());
            case OrderCancelled cancelled -> new OrderCancelledV1(cancelled.orderId(), cancelled.customerId(),
                    cancelled.previousStatus().name(), cancelled.reason());
            default -> throw new IllegalArgumentException("Evento sem mapeamento: " + event.getClass().getName());
        };
        String eventType = event.getClass().getSimpleName();
        var envelope = new EventEnvelope<>(event.eventId(), eventType, OrderEventMessages.VERSION,
                event.occurredAt(), data);
        return new OutboxMessage(event.eventId(), eventType, topic, event.aggregateId(), toJson(envelope));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento", e);
        }
    }
}
