package io.github.emanoeladolpho.order.domain.model;

import io.github.emanoeladolpho.order.domain.shared.DomainException;

import java.util.Objects;

/** Item do pedido (value object interno ao agregado Order). */
public record OrderItem(Sku sku, String productName, Money unitPrice, Quantity quantity) {

    public OrderItem {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(quantity, "quantity");
        if (productName == null || productName.isBlank() || productName.length() > 120) {
            throw new DomainException("INVALID_PRODUCT_NAME", "Nome do produto é obrigatório (até 120 caracteres)");
        }
        if (unitPrice.isZero()) {
            throw new DomainException("INVALID_UNIT_PRICE", "Preço unitário deve ser maior que zero");
        }
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity.value());
    }
}
