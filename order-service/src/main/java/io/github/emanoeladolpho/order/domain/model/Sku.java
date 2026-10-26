package io.github.emanoeladolpho.order.domain.model;

import io.github.emanoeladolpho.order.domain.shared.DomainException;

/** Código do produto (Stock Keeping Unit). */
public record Sku(String value) {

    public Sku {
        if (value == null || !value.matches("[A-Z0-9-]{3,40}")) {
            throw new DomainException("INVALID_SKU", "SKU deve ter de 3 a 40 caracteres: letras maiúsculas, números ou hífen");
        }
    }
}
