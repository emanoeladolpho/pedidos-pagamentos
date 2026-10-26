package io.github.emanoeladolpho.order.domain.model;

/**
 * <pre>
 * CREATED ──► PAYMENT_PENDING ──► PAID
 *    │              │              │
 *    │              └──► FAILED    │
 *    └──────────────┴──────────────┴──► CANCELLED
 * </pre>
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAYMENT_PENDING || target == PAID || target == FAILED || target == CANCELLED;
            case PAYMENT_PENDING -> target == PAID || target == FAILED || target == CANCELLED;
            case PAID -> target == CANCELLED;
            case FAILED, CANCELLED -> false;
        };
    }

    public boolean isFinal() {
        return this == FAILED || this == CANCELLED;
    }
}
