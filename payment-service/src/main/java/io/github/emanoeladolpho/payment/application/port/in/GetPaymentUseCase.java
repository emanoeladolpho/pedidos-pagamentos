package io.github.emanoeladolpho.payment.application.port.in;

import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;

public interface GetPaymentUseCase {

    Payment get(PaymentId paymentId, Requester requester);

    Payment getByOrder(OrderId orderId, Requester requester);
}
