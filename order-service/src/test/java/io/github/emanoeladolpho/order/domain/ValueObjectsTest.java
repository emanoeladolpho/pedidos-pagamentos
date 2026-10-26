package io.github.emanoeladolpho.order.domain;

import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.model.Quantity;
import io.github.emanoeladolpho.order.domain.model.Sku;
import io.github.emanoeladolpho.order.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1000})
    void quantityShouldBeBetweenOneAndMax(int value) {
        assertThatThrownBy(() -> new Quantity(value)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "sku-minusculo", "COM ESPACO"})
    void skuShouldFollowPattern(String value) {
        assertThatThrownBy(() -> new Sku(value)).isInstanceOf(DomainException.class);
    }

    @Test
    void customerIsRequired() {
        assertThatThrownBy(() -> new CustomerId(" ")).isInstanceOf(DomainException.class);
    }

    @Test
    void orderIdShouldRoundTripAsString() {
        OrderId id = OrderId.newId();
        assertThat(OrderId.of(id.toString())).isEqualTo(id);
    }

    @Test
    void itemShouldComputeSubtotal() {
        assertThat(OrderFixtures.item("ABC", "2.50", 4).subtotal()).isEqualTo(Money.of("10.00"));
    }

    @Test
    void itemShouldRejectZeroPriceAndBlankName() {
        assertThatThrownBy(() -> OrderFixtures.item("ABC", "0.00", 1))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_UNIT_PRICE");
        assertThatThrownBy(() -> new OrderItem(new Sku("ABC"), " ", Money.of("1.00"), new Quantity(1)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PRODUCT_NAME");
    }
}
