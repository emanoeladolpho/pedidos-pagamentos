package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(@Schema(example = "Produto com defeito") @NotBlank @Size(max = 500) String reason) {
}
