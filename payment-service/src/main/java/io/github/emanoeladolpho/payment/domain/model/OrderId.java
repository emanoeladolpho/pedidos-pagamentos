package io.github.emanoeladolpho.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Referência ao pedido de outro contexto: apenas o identificador, nunca o objeto. */
public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "OrderId não pode ser nulo");
    }

    public static OrderId of(String value) {
        return new OrderId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
