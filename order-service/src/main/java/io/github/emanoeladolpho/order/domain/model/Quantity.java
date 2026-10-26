package io.github.emanoeladolpho.order.domain.model;

import io.github.emanoeladolpho.order.domain.shared.DomainException;

public record Quantity(int value) {

    public static final int MAX = 999;

    public Quantity {
        if (value < 1 || value > MAX) {
            throw new DomainException("INVALID_QUANTITY", "Quantidade deve estar entre 1 e " + MAX);
        }
    }
}
