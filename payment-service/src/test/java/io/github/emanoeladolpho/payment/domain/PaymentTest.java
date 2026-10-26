package io.github.emanoeladolpho.payment.domain;

import io.github.emanoeladolpho.payment.domain.event.PaymentApproved;
import io.github.emanoeladolpho.payment.domain.event.PaymentDeclined;
import io.github.emanoeladolpho.payment.domain.event.PaymentInitiated;
import io.github.emanoeladolpho.payment.domain.event.PaymentRefunded;
import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;
import io.github.emanoeladolpho.payment.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final OrderId ORDER = new OrderId(UUID.randomUUID());
    private static final CustomerId CUSTOMER = new CustomerId("customer-1");

    private Payment pending() {
        Payment payment = Payment.initiate(ORDER, CUSTOMER, Money.of("99.90"));
        payment.pullDomainEvents();
        return payment;
    }

    @Test
    void initiateShouldCreatePendingPaymentAndRegisterEvent() {
        Payment payment = Payment.initiate(ORDER, CUSTOMER, Money.of("99.90"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.isPending()).isTrue();
        assertThat(payment.pullDomainEvents()).singleElement().isInstanceOfSatisfying(PaymentInitiated.class, e -> {
            assertThat(e.orderId()).isEqualTo(ORDER.toString());
            assertThat(e.aggregateId()).isEqualTo(ORDER.toString());
            assertThat(e.amount()).isEqualTo(Money.of("99.90"));
        });
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> Payment.initiate(ORDER, CUSTOMER, Money.of("0")))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PAYMENT_AMOUNT");
    }

    @Test
    void approveShouldStoreTransactionAndRegisterEvent() {
        Payment payment = pending();

        payment.approve("gw-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getGatewayTransactionId()).isEqualTo("gw-123");
        assertThat(payment.pullDomainEvents()).singleElement().isInstanceOf(PaymentApproved.class);
    }

    @Test
    void approveRequiresTransactionId() {
        assertThatThrownBy(() -> pending().approve(" "))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("MISSING_TRANSACTION_ID");
    }

    @Test
    void declineShouldStoreReason() {
        Payment payment = pending();

        payment.decline("Cartão recusado");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getFailureReason()).isEqualTo("Cartão recusado");
        assertThat(payment.pullDomainEvents()).singleElement().isInstanceOf(PaymentDeclined.class);
    }

    @Test
    void refundOnlyApprovedPayments() {
        Payment payment = pending();
        assertThatThrownBy(() -> payment.refund("x"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("PAYMENT_NOT_REFUNDABLE");

        payment.approve("gw-1");
        payment.pullDomainEvents();
        payment.refund("Pedido cancelado");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.pullDomainEvents()).singleElement().isInstanceOfSatisfying(PaymentRefunded.class,
                e -> assertThat(e.reason()).isEqualTo("Pedido cancelado"));
    }

    @Test
    void cancelOnlyPendingPayments() {
        Payment payment = pending();
        payment.cancel();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.pullDomainEvents()).isEmpty();

        assertThatThrownBy(payment::cancel).isInstanceOf(DomainException.class);
    }

    @Test
    void restoreAndOwnership() {
        Payment payment = Payment.restore(pending().getId(), ORDER, CUSTOMER, new Money(BigDecimal.TEN, "BRL"),
                PaymentStatus.APPROVED, "gw", null, java.time.Instant.now(), java.time.Instant.now(), 2L);

        assertThat(payment.pullDomainEvents()).isEmpty();
        assertThat(payment.getVersion()).isEqualTo(2L);
        assertThat(payment.belongsTo(CUSTOMER)).isTrue();
        assertThat(payment.belongsTo(new CustomerId("outro"))).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PENDING, APPROVED, true", "PENDING, DECLINED, true", "PENDING, CANCELLED, true",
            "PENDING, REFUNDED, false", "APPROVED, REFUNDED, true", "APPROVED, DECLINED, false",
            "DECLINED, APPROVED, false", "REFUNDED, APPROVED, false", "CANCELLED, APPROVED, false"
    })
    void transitions(PaymentStatus from, PaymentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void moneyValidation() {
        assertThat(Money.of("10").amount()).isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> Money.of("-1")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Money.of("1.234")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "x")).isInstanceOf(DomainException.class);
    }
}
