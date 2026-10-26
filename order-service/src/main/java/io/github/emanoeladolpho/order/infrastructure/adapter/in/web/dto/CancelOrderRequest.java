package io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Schema(example = "Desisti da compra") @Size(max = 500) String reason) {
}
