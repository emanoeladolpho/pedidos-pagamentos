package io.github.emanoeladolpho.order.domain.model;

import io.github.emanoeladolpho.order.domain.shared.DomainException;

/** Identificador do cliente no provedor de identidade (claim "sub" do token). */
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
