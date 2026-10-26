package io.github.emanoeladolpho.order.domain;

import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "CREATED, PAYMENT_PENDING, true",
            "CREATED, CANCELLED, true",
            "PAYMENT_PENDING, PAID, true",
            "PAYMENT_PENDING, FAILED, true",
            "PAYMENT_PENDING, CREATED, false",
            "PAID, CANCELLED, true",
            "PAID, FAILED, false",
            "FAILED, CANCELLED, false",
            "CANCELLED, PAID, false"
    })
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
