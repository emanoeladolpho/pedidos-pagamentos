package io.github.emanoeladolpho.payment.application.port.out;

import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;

import java.util.Optional;

public interface PaymentRepository {

    void save(Payment payment);

    Optional<Payment> findById(PaymentId id);

    Optional<Payment> findByOrderId(OrderId orderId);
}
