package io.github.emanoeladolpho.chassis.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Formato de todas as mensagens trocadas entre os serviços.
 * {@code eventVersion} permite evoluir o schema de {@code data} sem quebrar consumidores antigos.
 */
public record EventEnvelope<T>(UUID eventId, String eventType, int eventVersion, Instant occurredAt, T data) {
}
