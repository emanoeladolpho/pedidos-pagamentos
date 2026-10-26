package io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging;

import io.github.emanoeladolpho.payment.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.payment.domain.shared.DomainEvent;
import io.github.emanoeladolpho.chassis.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/** "Publicar" um evento = gravá-lo no outbox, na transação corrente. O relay envia ao Kafka depois. */
@Component
class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final PaymentEventMapper mapper;
    private final OutboxWriter outboxWriter;

    OutboxDomainEventPublisher(PaymentEventMapper mapper, OutboxWriter outboxWriter) {
        this.mapper = mapper;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        events.stream().map(mapper::toMessage).forEach(outboxWriter::append);
    }
}
