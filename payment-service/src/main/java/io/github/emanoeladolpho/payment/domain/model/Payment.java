package io.github.emanoeladolpho.payment.domain.model;

import io.github.emanoeladolpho.payment.domain.event.PaymentApproved;
import io.github.emanoeladolpho.payment.domain.event.PaymentDeclined;
import io.github.emanoeladolpho.payment.domain.event.PaymentInitiated;
import io.github.emanoeladolpho.payment.domain.event.PaymentRefunded;
import io.github.emanoeladolpho.payment.domain.shared.AggregateRoot;
import io.github.emanoeladolpho.payment.domain.shared.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root do pagamento de um pedido. Existe no máximo um pagamento por pedido,
 * o que torna o processamento naturalmente idempotente pelo identificador do pedido.
 */
public class Payment extends AggregateRoot {

    private final PaymentId id;
    private final OrderId orderId;
    private final CustomerId customerId;
    private final Money amount;
    private final Instant createdAt;
    private PaymentStatus status;
    private String gatewayTransactionId;
    private String failureReason;
    private Instant updatedAt;
    /** Versão para lock otimista; nula enquanto o agregado não foi persistido. */
    private final Long version;

    private Payment(PaymentId id, OrderId orderId, CustomerId customerId, Money amount, PaymentStatus status,
                    String gatewayTransactionId, String failureReason, Instant createdAt, Instant updatedAt,
                    Long version) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        this.status = Objects.requireNonNull(status);
        this.gatewayTransactionId = gatewayTransactionId;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Payment initiate(OrderId orderId, CustomerId customerId, Money amount) {
        if (amount.isZero()) {
            throw new DomainException("INVALID_PAYMENT_AMOUNT", "O valor do pagamento deve ser maior que zero");
        }
        Instant now = Instant.now();
        Payment payment = new Payment(PaymentId.newId(), orderId, customerId, amount, PaymentStatus.PENDING,
                null, null, now, now, null);
        payment.registerEvent(new PaymentInitiated(UUID.randomUUID(), now, payment.id.toString(),
                orderId.toString(), amount));
        return payment;
    }

    public static Payment restore(PaymentId id, OrderId orderId, CustomerId customerId, Money amount,
                                  PaymentStatus status, String gatewayTransactionId, String failureReason,
                                  Instant createdAt, Instant updatedAt, Long version) {
        return new Payment(id, orderId, customerId, amount, status, gatewayTransactionId, failureReason,
                createdAt, updatedAt, version);
    }

    public void approve(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new DomainException("MISSING_TRANSACTION_ID", "Aprovação exige o identificador da transação no gateway");
        }
        changeStatus(PaymentStatus.APPROVED);
        this.gatewayTransactionId = transactionId;
        registerEvent(new PaymentApproved(UUID.randomUUID(), updatedAt, id.toString(), orderId.toString(),
                amount, transactionId));
    }

    public void decline(String reason) {
        changeStatus(PaymentStatus.DECLINED);
        this.failureReason = reason;
        registerEvent(new PaymentDeclined(UUID.randomUUID(), updatedAt, id.toString(), orderId.toString(), reason));
    }

    public void refund(String reason) {
        if (status != PaymentStatus.APPROVED) {
            throw new DomainException("PAYMENT_NOT_REFUNDABLE",
                    "Só é possível estornar pagamentos aprovados (status atual: %s)".formatted(status));
        }
        changeStatus(PaymentStatus.REFUNDED);
        registerEvent(new PaymentRefunded(UUID.randomUUID(), updatedAt, id.toString(), orderId.toString(),
                amount, reason));
    }

    /** Pedido cancelado antes de a cobrança acontecer: o pagamento não deve mais ser processado. */
    public void cancel() {
        changeStatus(PaymentStatus.CANCELLED);
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean belongsTo(CustomerId customer) {
        return customerId.equals(customer);
    }

    private void changeStatus(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new DomainException("INVALID_PAYMENT_STATUS_TRANSITION",
                    "Não é possível alterar o pagamento de %s para %s".formatted(status, target));
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public PaymentId getId() {
        return id;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
