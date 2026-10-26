package io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validação de formato na borda; as regras de negócio (SKU repetido, moeda única...) ficam no domínio.
 */
public record PlaceOrderRequest(
        @Schema(example = "BRL") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items
) {

    public record Item(
            @Schema(example = "TECLADO-MEC-01") @NotBlank @Pattern(regexp = "[A-Z0-9-]{3,40}") String sku,
            @Schema(example = "Teclado mecânico") @NotBlank @Size(max = 200) String productName,
            @Schema(example = "349.90") @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @Schema(example = "1") @Min(1) @Max(999) int quantity
    ) {
    }
}
