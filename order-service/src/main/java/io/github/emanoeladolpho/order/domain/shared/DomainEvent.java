package io.github.emanoeladolpho.order.domain.shared;

import java.time.Instant;
import java.util.UUID;

/** Fato de negócio que já aconteceu. Imutável e nomeado no passado. */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    /** Identificador do agregado que originou o evento (usado como chave de partição). */
    String aggregateId();
}
