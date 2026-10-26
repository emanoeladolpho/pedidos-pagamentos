package io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String status,
        List<Item> items,
        BigDecimal totalAmount,
        String currency,
        String failureReason,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {

    public record Item(String sku, String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
    }
}
