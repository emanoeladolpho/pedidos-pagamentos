package io.github.emanoeladolpho.payment.domain.model;

import io.github.emanoeladolpho.payment.domain.shared.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário com moeda. Sempre 2 casas decimais; nunca negativo.
 */
public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "BRL";

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new DomainException("INVALID_CURRENCY", "Moeda deve seguir o padrão ISO 4217 (ex.: BRL)");
        }
        if (amount.signum() < 0) {
            throw new DomainException("NEGATIVE_AMOUNT", "Valor monetário não pode ser negativo");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new DomainException("INVALID_AMOUNT_SCALE", "Valor monetário aceita no máximo 2 casas decimais");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount), DEFAULT_CURRENCY);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }
}
