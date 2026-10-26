package io.github.emanoeladolpho.order.application.port.out;

import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.OrderId;

import java.util.Optional;

/** Guarda as chaves de idempotência já usadas na criação de pedidos (escopo: cliente + chave). */
public interface IdempotencyStore {

    Optional<Entry> find(CustomerId customerId, String key);

    void save(Entry entry);

    /** @param requestHash hash do conteúdo da requisição, para detectar reuso da chave com outro payload */
    record Entry(CustomerId customerId, String key, String requestHash, OrderId orderId) {
    }
}
