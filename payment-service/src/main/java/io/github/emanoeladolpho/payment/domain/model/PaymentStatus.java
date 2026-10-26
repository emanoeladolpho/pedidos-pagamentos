package io.github.emanoeladolpho.payment.domain.model;

/**
 * <pre>
 * PENDING ──► APPROVED ──► REFUNDED
 *    ├──────► DECLINED
 *    └──────► CANCELLED   (pedido cancelado antes da cobrança)
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    DECLINED,
    REFUNDED,
    CANCELLED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == APPROVED || target == DECLINED || target == CANCELLED;
            case APPROVED -> target == REFUNDED;
            case DECLINED, REFUNDED, CANCELLED -> false;
        };
    }
}
