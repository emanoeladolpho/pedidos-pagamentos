package io.github.emanoeladolpho.order.domain.model;

import io.github.emanoeladolpho.order.domain.event.OrderCancelled;
import io.github.emanoeladolpho.order.domain.event.OrderCreated;
import io.github.emanoeladolpho.order.domain.shared.AggregateRoot;
import io.github.emanoeladolpho.order.domain.shared.DomainException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root do pedido. Toda mudança de estado passa por aqui, garantindo as invariantes:
 * pedido com 1 a {@value #MAX_ITEMS} itens, sem SKU repetido, todos na mesma moeda, total consistente
 * e transições de status válidas.
 */
public class Order extends AggregateRoot {

    public static final int MAX_ITEMS = 50;

    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderItem> items;
    private final Money total;
    private final Instant createdAt;
    private OrderStatus status;
    private String failureReason;
    private String cancellationReason;
    private Instant updatedAt;
    private final long version;

    private Order(OrderId id, CustomerId customerId, List<OrderItem> items, Money total, OrderStatus status,
                  String failureReason, String cancellationReason, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.items = List.copyOf(items);
        this.total = Objects.requireNonNull(total);
        this.status = Objects.requireNonNull(status);
        this.failureReason = failureReason;
        this.cancellationReason = cancellationReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** Cria um novo pedido e registra o evento {@link OrderCreated}. */
    public static Order place(CustomerId customerId, List<OrderItem> items) {
        validateItems(items);
        Money total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.zero(items.getFirst().unitPrice().currency()), Money::add);
        Instant now = Instant.now();
        Order order = new Order(OrderId.newId(), customerId, items, total, OrderStatus.CREATED,
                null, null, now, now, 0);
        order.registerEvent(new OrderCreated(UUID.randomUUID(), now, order.id.toString(),
                customerId.value(), order.items, total));
        return order;
    }

    /** Reconstrói um pedido já persistido (não gera eventos). */
    public static Order restore(OrderId id, CustomerId customerId, List<OrderItem> items, Money total,
                                OrderStatus status, String failureReason, String cancellationReason,
                                Instant createdAt, Instant updatedAt, long version) {
        return new Order(id, customerId, items, total, status, failureReason, cancellationReason,
                createdAt, updatedAt, version);
    }

    private static void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("EMPTY_ORDER", "O pedido deve ter pelo menos um item");
        }
        if (items.size() > MAX_ITEMS) {
            throw new DomainException("TOO_MANY_ITEMS", "O pedido aceita no máximo " + MAX_ITEMS + " itens");
        }
        var skus = new HashSet<Sku>();
        String currency = items.getFirst().unitPrice().currency();
        for (OrderItem item : items) {
            if (!skus.add(item.sku())) {
                throw new DomainException("DUPLICATED_SKU", "SKU repetido no pedido: " + item.sku().value());
            }
            if (!item.unitPrice().currency().equals(currency)) {
                throw new DomainException("CURRENCY_MISMATCH", "Todos os itens devem estar na mesma moeda");
            }
        }
    }

    // ---- Reações ao resultado do pagamento (eventos podem chegar repetidos ou atrasados) ----

    /** O payment-service recebeu o pedido e iniciou a cobrança. Ignorado se o pedido já avançou. */
    public void markPaymentPending() {
        if (status == OrderStatus.CREATED) {
            changeStatus(OrderStatus.PAYMENT_PENDING);
        }
    }

    /**
     * Pagamento aprovado. Se o pedido foi cancelado nesse meio tempo, permanece cancelado:
     * o payment-service processa o OrderCancelled em seguida e faz o estorno.
     */
    public void markPaid() {
        switch (status) {
            case CREATED, PAYMENT_PENDING -> changeStatus(OrderStatus.PAID);
            case PAID, CANCELLED -> { /* evento repetido ou atrasado: nada a fazer */ }
            case FAILED -> throw invalidTransition(OrderStatus.PAID);
        }
    }

    public void markPaymentFailed(String reason) {
        switch (status) {
            case CREATED, PAYMENT_PENDING -> {
                changeStatus(OrderStatus.FAILED);
                this.failureReason = reason;
            }
            case FAILED, CANCELLED -> { /* evento repetido ou atrasado */ }
            case PAID -> throw invalidTransition(OrderStatus.FAILED);
        }
    }

    /** Estorno feito diretamente no payment-service (ex.: pelo suporte): o pedido pago é cancelado. */
    public void markRefunded(String reason) {
        if (status == OrderStatus.PAID) {
            changeStatus(OrderStatus.CANCELLED);
            this.cancellationReason = reason;
        }
    }

    // ---- Comandos do usuário ----

    public void cancel(String reason) {
        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw invalidTransition(OrderStatus.CANCELLED);
        }
        OrderStatus previous = status;
        changeStatus(OrderStatus.CANCELLED);
        this.cancellationReason = reason;
        registerEvent(new OrderCancelled(UUID.randomUUID(), updatedAt, id.toString(), customerId.value(),
                previous, reason));
    }

    public boolean isOwnedBy(CustomerId customer) {
        return customerId.equals(customer);
    }

    private void changeStatus(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw invalidTransition(target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    private DomainException invalidTransition(OrderStatus target) {
        return new DomainException("INVALID_ORDER_STATUS_TRANSITION",
                "Não é possível alterar o pedido de %s para %s".formatted(status, target));
    }

    public OrderId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Money getTotal() {
        return total;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
