package io.github.emanoeladolpho.chassis.outbox;

import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava eventos no outbox. Exige uma transação já aberta ({@code MANDATORY}): o evento só existe
 * se a alteração de negócio que o gerou também for confirmada — esse é o ponto do Transactional Outbox.
 */
public class OutboxWriter {

    private final OutboxEventRepository repository;

    public OutboxWriter(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxMessage message) {
        repository.save(new OutboxEventEntity(message, CorrelationId.currentOrNew()));
    }
}
