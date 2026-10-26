package io.github.emanoeladolpho.order.domain;

import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldNormalizeScaleAndAdd() {
        Money total = Money.of("10").add(Money.of("0.5"));

        assertThat(total.amount()).isEqualByComparingTo("10.50");
        assertThat(total.amount().scale()).isEqualTo(2);
        assertThat(total).isEqualTo(Money.of("10.50"));
    }

    @Test
    void shouldMultiply() {
        assertThat(Money.of("19.90").multiply(3)).isEqualTo(Money.of("59.70"));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-1.00"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("NEGATIVE_AMOUNT");
    }

    @Test
    void shouldRejectMoreThanTwoDecimals() {
        assertThatThrownBy(() -> Money.of("1.001"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_AMOUNT_SCALE");
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "real"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CURRENCY");
    }

    @Test
    void shouldRejectOperationsBetweenCurrencies() {
        Money usd = new Money(BigDecimal.ONE, "USD");

        assertThatThrownBy(() -> Money.of("1.00").add(usd))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
    }
}
