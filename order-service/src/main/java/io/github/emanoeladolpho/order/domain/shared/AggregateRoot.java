package io.github.emanoeladolpho.order.domain.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Raiz de agregado: acumula os eventos de domínio gerados pelas operações,
 * que a camada de aplicação recolhe e publica depois de persistir o estado.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Devolve os eventos pendentes e limpa a lista (cada evento é publicado uma única vez). */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
