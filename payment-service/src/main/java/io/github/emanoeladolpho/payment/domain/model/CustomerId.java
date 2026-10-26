package io.github.emanoeladolpho.payment.domain.model;

import io.github.emanoeladolpho.payment.domain.shared.DomainException;

public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new DomainException("INVALID_CUSTOMER", "Cliente é obrigatório");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
