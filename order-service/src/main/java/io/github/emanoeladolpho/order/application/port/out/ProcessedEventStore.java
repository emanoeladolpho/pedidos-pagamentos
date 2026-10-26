package io.github.emanoeladolpho.order.application.port.out;

import java.util.UUID;

/** Registro de eventos já consumidos (inbox), base da idempotência dos consumidores. */
public interface ProcessedEventStore {

    boolean isProcessed(UUID eventId, String consumer);

    void markProcessed(UUID eventId, String consumer);
}
