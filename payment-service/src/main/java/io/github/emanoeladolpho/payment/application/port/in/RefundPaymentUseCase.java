package io.github.emanoeladolpho.payment.application.port.in;

import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;

/** Estorno manual (ex.: atendimento). A autorização (ADMIN) é garantida pelo adapter de entrada. */
public interface RefundPaymentUseCase {

    Payment refund(PaymentId paymentId, String reason);
}
