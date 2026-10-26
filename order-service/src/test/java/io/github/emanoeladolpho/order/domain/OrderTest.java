package io.github.emanoeladolpho.order.domain;

import io.github.emanoeladolpho.order.domain.event.OrderCancelled;
import io.github.emanoeladolpho.order.domain.event.OrderCreated;
import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import io.github.emanoeladolpho.order.domain.shared.DomainEvent;
import io.github.emanoeladolpho.order.domain.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static io.github.emanoeladolpho.order.domain.OrderFixtures.CUSTOMER;
import static io.github.emanoeladolpho.order.domain.OrderFixtures.item;
import static io.github.emanoeladolpho.order.domain.OrderFixtures.placedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Nested
    class Place {

        @Test
        void shouldCalculateTotalAndRegisterOrderCreated() {
            Order order = Order.place(CUSTOMER, List.of(item("SKU-1", "10.00", 2), item("SKU-2", "5.50", 1)));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getTotal()).isEqualTo(Money.of("25.50"));

            List<DomainEvent> events = order.pullDomainEvents();
            assertThat(events).singleElement().isInstanceOfSatisfying(OrderCreated.class, event -> {
                assertThat(event.orderId()).isEqualTo(order.getId().toString());
                assertThat(event.total()).isEqualTo(Money.of("25.50"));
                assertThat(event.items()).hasSize(2);
                assertThat(event.aggregateId()).isEqualTo(event.orderId());
            });
            assertThat(order.pullDomainEvents()).as("eventos são entregues uma única vez").isEmpty();
        }

        @Test
        void shouldRejectEmptyOrder() {
            assertThatThrownBy(() -> Order.place(CUSTOMER, Collections.emptyList()))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("EMPTY_ORDER");
        }

        @Test
        void shouldRejectTooManyItems() {
            List<OrderItem> items = IntStream.rangeClosed(1, Order.MAX_ITEMS + 1)
                    .mapToObj(i -> item("SKU-" + i, "1.00", 1)).toList();

            assertThatThrownBy(() -> Order.place(CUSTOMER, items))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("TOO_MANY_ITEMS");
        }

        @Test
        void shouldRejectDuplicatedSku() {
            assertThatThrownBy(() -> Order.place(CUSTOMER, List.of(item("SKU-1", "1.00", 1), item("SKU-1", "2.00", 1))))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("DUPLICATED_SKU");
        }

        @Test
        void shouldRejectMixedCurrencies() {
            OrderItem usd = new OrderItem(new io.github.emanoeladolpho.order.domain.model.Sku("SKU-2"), "Importado",
                    new Money(BigDecimal.TEN, "USD"), new io.github.emanoeladolpho.order.domain.model.Quantity(1));

            assertThatThrownBy(() -> Order.place(CUSTOMER, List.of(item("SKU-1", "1.00", 1), usd)))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
        }
    }

    @Nested
    class PaymentResult {

        @Test
        void shouldFollowHappyPath() {
            Order order = placedOrder();

            order.markPaymentPending();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

            order.markPaid();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        void shouldBeIdempotentForRepeatedEvents() {
            Order order = placedOrder();
            order.markPaid();
            order.markPaid();
            order.markPaymentPending();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        void shouldRegisterFailureReason() {
            Order order = placedOrder();
            order.markPaymentFailed("Saldo insuficiente");
            order.markPaymentFailed("repetido");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getFailureReason()).isEqualTo("Saldo insuficiente");
        }

        @Test
        void approvalAfterCancellationKeepsOrderCancelled() {
            Order order = placedOrder();
            order.cancel("Desisti");

            order.markPaid();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void shouldRejectContradictoryResults() {
            Order paid = placedOrder();
            paid.markPaid();
            assertThatThrownBy(() -> paid.markPaymentFailed("x")).isInstanceOf(DomainException.class);

            Order failed = placedOrder();
            failed.markPaymentFailed("x");
            assertThatThrownBy(failed::markPaid).isInstanceOf(DomainException.class);
        }

        @Test
        void refundShouldCancelPaidOrder() {
            Order order = placedOrder();
            order.markPaid();

            order.markRefunded("Estorno pelo suporte");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancellationReason()).isEqualTo("Estorno pelo suporte");
            assertThat(order.pullDomainEvents()).as("não gera novo pedido de estorno").isEmpty();
        }

        @Test
        void refundOfNonPaidOrderIsIgnored() {
            Order order = placedOrder();
            order.markRefunded("x");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        }
    }

    @Nested
    class Cancel {

        @Test
        void shouldCancelAndRegisterEventWithPreviousStatus() {
            Order order = placedOrder();
            order.markPaid();

            order.cancel("Comprei errado");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.pullDomainEvents()).singleElement().isInstanceOfSatisfying(OrderCancelled.class, event -> {
                assertThat(event.previousStatus()).isEqualTo(OrderStatus.PAID);
                assertThat(event.reason()).isEqualTo("Comprei errado");
            });
        }

        @Test
        void shouldNotCancelTwiceOrCancelFailedOrder() {
            Order cancelled = placedOrder();
            cancelled.cancel(null);
            assertThatThrownBy(() -> cancelled.cancel(null))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_ORDER_STATUS_TRANSITION");

            Order failed = placedOrder();
            failed.markPaymentFailed("x");
            assertThatThrownBy(() -> failed.cancel(null)).isInstanceOf(DomainException.class);
        }
    }

    @Test
    void shouldCheckOwnership() {
        Order order = placedOrder();
        assertThat(order.isOwnedBy(CUSTOMER)).isTrue();
        assertThat(order.isOwnedBy(new CustomerId("outro"))).isFalse();
    }

    @Test
    void restoreShouldNotRegisterEvents() {
        Order original = placedOrder();
        Order restored = Order.restore(original.getId(), original.getCustomerId(), original.getItems(),
                original.getTotal(), OrderStatus.PAYMENT_PENDING, null, null, original.getCreatedAt(),
                original.getUpdatedAt(), 3);

        assertThat(restored.pullDomainEvents()).isEmpty();
        assertThat(restored.getVersion()).isEqualTo(3);
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }
}
