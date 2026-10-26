package io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging;

import java.math.BigDecimal;

/** Contrato público (versão 1) dos eventos publicados no tópico de pagamentos. */
public final class PaymentEventMessages {

    public static final int VERSION = 1;

    private PaymentEventMessages() {
    }

    public record PaymentInitiatedV1(String paymentId, String orderId, BigDecimal amount, String currency) {
    }

    public record PaymentApprovedV1(String paymentId, String orderId, BigDecimal amount, String currency,
                                    String gatewayTransactionId) {
    }

    public record PaymentDeclinedV1(String paymentId, String orderId, String reason) {
    }

    public record PaymentRefundedV1(String paymentId, String orderId, BigDecimal amount, String currency,
                                    String reason) {
    }
}
