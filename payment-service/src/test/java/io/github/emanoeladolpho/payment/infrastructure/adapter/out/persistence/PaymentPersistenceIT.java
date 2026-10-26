package io.github.emanoeladolpho.payment.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;
import io.github.emanoeladolpho.payment.infrastructure.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.config.name=payment-service")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PaymentPersistenceAdapter.class, JpaUnitOfWork.class,
        PaymentPersistenceMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentPersistenceIT {

    @Autowired
    private PaymentPersistenceAdapter payments;
    @Autowired
    private JpaUnitOfWork unitOfWork;

    private Payment newPayment() {
        return Payment.initiate(new OrderId(UUID.randomUUID()), new CustomerId("c1"), Money.of("150.00"));
    }

    @Test
    void shouldSaveFindAndUpdate() {
        Payment payment = newPayment();
        unitOfWork.run(() -> payments.save(payment));

        unitOfWork.run(() -> {
            Payment loaded = payments.findByOrderId(payment.getOrderId()).orElseThrow();
            loaded.approve("gw-1");
            payments.save(loaded);
        });

        Payment reloaded = payments.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(reloaded.getGatewayTransactionId()).isEqualTo("gw-1");
        assertThat(reloaded.getAmount()).isEqualTo(Money.of("150.00"));
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void databaseShouldAllowOnlyOnePaymentPerOrder() {
        Payment first = newPayment();
        unitOfWork.run(() -> payments.save(first));
        Payment duplicate = Payment.initiate(first.getOrderId(), first.getCustomerId(), first.getAmount());

        assertThatThrownBy(() -> unitOfWork.run(() -> payments.save(duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
