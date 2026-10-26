package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.exception.IdempotencyKeyReusedException;
import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase;
import io.github.emanoeladolpho.order.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.order.application.port.out.IdempotencyStore;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.model.Quantity;
import io.github.emanoeladolpho.order.domain.model.Sku;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Cria o pedido, registra a chave de idempotência e grava o evento OrderCreated no outbox,
 * tudo na mesma transação: ou tudo é persistido, ou nada é.
 */
public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository orderRepository;
    private final IdempotencyStore idempotencyStore;
    private final DomainEventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public PlaceOrderService(OrderRepository orderRepository, IdempotencyStore idempotencyStore,
                             DomainEventPublisher eventPublisher, UnitOfWork unitOfWork) {
        this.orderRepository = orderRepository;
        this.idempotencyStore = idempotencyStore;
        this.eventPublisher = eventPublisher;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Result place(Command command) {
        String requestHash = hash(command);
        return unitOfWork.execute(() -> {
            Optional<IdempotencyStore.Entry> previous =
                    idempotencyStore.find(command.requester().customerId(), command.idempotencyKey());
            if (previous.isPresent()) {
                return replay(previous.get(), requestHash, command.idempotencyKey());
            }

            List<OrderItem> items = command.items().stream()
                    .map(item -> new OrderItem(new Sku(item.sku()), item.productName(),
                            new Money(item.unitPrice(), command.currency()), new Quantity(item.quantity())))
                    .toList();
            Order order = Order.place(command.requester().customerId(), items);

            orderRepository.save(order);
            idempotencyStore.save(new IdempotencyStore.Entry(command.requester().customerId(),
                    command.idempotencyKey(), requestHash, order.getId()));
            eventPublisher.publish(order.pullDomainEvents());
            return new Result(order, false);
        });
    }

    private Result replay(IdempotencyStore.Entry entry, String requestHash, String key) {
        if (!entry.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(key);
        }
        Order original = orderRepository.findById(entry.orderId())
                .orElseThrow(() -> new OrderNotFoundException(entry.orderId()));
        return new Result(original, true);
    }

    /** Hash canônico do conteúdo do pedido (ignora o solicitante e a própria chave). */
    static String hash(Command command) {
        StringBuilder canonical = new StringBuilder(command.currency());
        command.items().forEach(item -> canonical.append('|').append(item.sku())
                .append(';').append(item.productName())
                .append(';').append(item.unitPrice().stripTrailingZeros().toPlainString())
                .append(';').append(item.quantity()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
