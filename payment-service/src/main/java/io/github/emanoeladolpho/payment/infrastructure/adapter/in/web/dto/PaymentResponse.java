package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String status,
        String gatewayTransactionId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
