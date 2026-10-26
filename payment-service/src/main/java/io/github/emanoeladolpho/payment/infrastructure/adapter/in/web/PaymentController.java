package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.payment.application.port.in.GetPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.RefundPaymentUseCase;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.emanoeladolpho.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import io.github.emanoeladolpho.payment.infrastructure.adapter.in.web.dto.RefundRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@Tag(name = "Pagamentos")
@SecurityRequirement(name = "bearer")
class PaymentController {

    private final GetPaymentUseCase getPayment;
    private final RefundPaymentUseCase refundPayment;
    private final PaymentWebMapper mapper;

    PaymentController(GetPaymentUseCase getPayment, RefundPaymentUseCase refundPayment, PaymentWebMapper mapper) {
        this.getPayment = getPayment;
        this.refundPayment = refundPayment;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um pagamento", description = "Clientes só enxergam os próprios pagamentos.")
    @ApiResponse(responseCode = "404", description = "Pagamento inexistente ou de outro cliente")
    PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mapper.toResponse(getPayment.get(new PaymentId(id), AuthenticatedRequester.from(jwt)));
    }

    @GetMapping
    @Operation(summary = "Consulta o pagamento de um pedido")
    @ApiResponse(responseCode = "404", description = "Pedido sem pagamento (ainda) ou de outro cliente")
    PaymentResponse getByOrder(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID orderId) {
        return mapper.toResponse(getPayment.getByOrder(new OrderId(orderId), AuthenticatedRequester.from(jwt)));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Estorna um pagamento aprovado (somente ADMIN)",
            description = "O order-service recebe PaymentRefunded e cancela o pedido.")
    @ApiResponse(responseCode = "403", description = "Requer a role ADMIN")
    @ApiResponse(responseCode = "422", description = "Pagamento não está aprovado ou o gateway recusou o estorno")
    @ApiResponse(responseCode = "503", description = "Gateway de pagamento indisponível")
    PaymentResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return mapper.toResponse(refundPayment.refund(new PaymentId(id), request.reason()));
    }
}
