package io.github.emanoeladolpho.chassis.outbox;

import java.util.Objects;
import java.util.UUID;

/**
 * Evento pronto para ser publicado: já serializado e com destino definido.
 *
 * @param key chave da mensagem no Kafka (define a partição e, portanto, a ordem)
 */
public record OutboxMessage(UUID eventId, String eventType, String topic, String key, String payload) {

    public OutboxMessage {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(key);
        Objects.requireNonNull(payload);
    }
}
