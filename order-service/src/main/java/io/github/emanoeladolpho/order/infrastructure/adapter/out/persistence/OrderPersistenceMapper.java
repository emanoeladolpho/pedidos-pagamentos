package io.github.emanoeladolpho.order.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import io.github.emanoeladolpho.order.domain.model.Quantity;
import io.github.emanoeladolpho.order.domain.model.Sku;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

/** Converte entre o agregado de domínio e o modelo de persistência. */
@Mapper
public interface OrderPersistenceMapper {

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "customerId", source = "customerId.value")
    @Mapping(target = "totalAmount", source = "total.amount")
    @Mapping(target = "currency", source = "total.currency")
    OrderJpaEntity toEntity(Order order);

    @Mapping(target = "sku", source = "sku.value")
    @Mapping(target = "unitPrice", source = "unitPrice.amount")
    @Mapping(target = "quantity", source = "quantity.value")
    OrderItemEmbeddable toEmbeddable(OrderItem item);

    /** A reconstrução passa pela fábrica do domínio, que valida as invariantes. */
    default Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(item -> new OrderItem(new Sku(item.getSku()), item.getProductName(),
                        new Money(item.getUnitPrice(), entity.getCurrency()), new Quantity(item.getQuantity())))
                .toList();
        return Order.restore(new OrderId(entity.getId()), new CustomerId(entity.getCustomerId()), items,
                new Money(entity.getTotalAmount(), entity.getCurrency()), OrderStatus.valueOf(entity.getStatus()),
                entity.getFailureReason(), entity.getCancellationReason(), entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getVersion());
    }

    default UUID map(OrderId id) {
        return id.value();
    }
}
