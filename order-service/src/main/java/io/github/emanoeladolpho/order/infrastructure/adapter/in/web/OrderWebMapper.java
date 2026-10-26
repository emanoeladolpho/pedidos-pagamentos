package io.github.emanoeladolpho.order.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import io.github.emanoeladolpho.order.domain.model.OrderItem;
import io.github.emanoeladolpho.order.domain.model.Quantity;
import io.github.emanoeladolpho.order.domain.model.Sku;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto.OrderResponse;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto.PlaceOrderRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversões entre os DTOs HTTP e o modelo da aplicação. */
@Mapper
interface OrderWebMapper {

    @Mapping(target = "items", source = "request.items")
    PlaceOrderUseCase.Command toCommand(PlaceOrderRequest request, Requester requester, String idempotencyKey);

    @Mapping(target = "totalAmount", source = "total.amount")
    @Mapping(target = "currency", source = "total.currency")
    OrderResponse toResponse(Order order);

    @Mapping(target = "unitPrice", source = "unitPrice.amount")
    @Mapping(target = "subtotal", expression = "java(item.subtotal().amount())")
    OrderResponse.Item toResponse(OrderItem item);

    default String map(OrderId id) {
        return id.toString();
    }

    default String map(CustomerId id) {
        return id.value();
    }

    default String map(Sku sku) {
        return sku.value();
    }

    default int map(Quantity quantity) {
        return quantity.value();
    }
}
