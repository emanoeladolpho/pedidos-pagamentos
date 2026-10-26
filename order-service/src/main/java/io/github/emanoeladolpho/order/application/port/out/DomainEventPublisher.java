package io.github.emanoeladolpho.order.application.port.out;

import io.github.emanoeladolpho.order.domain.shared.DomainEvent;

import java.util.List;

/**
 * Publica eventos de domínio para outros serviços. Deve ser chamado dentro da mesma
 * {@link UnitOfWork} que persiste o agregado: a implementação grava no outbox, garantindo atomicidade.
 */
public interface DomainEventPublisher {

    void publish(List<DomainEvent> events);
}
