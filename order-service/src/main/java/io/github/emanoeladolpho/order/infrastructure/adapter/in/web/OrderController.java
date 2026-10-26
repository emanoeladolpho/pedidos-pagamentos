package io.github.emanoeladolpho.order.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.order.application.port.in.CancelOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.GetOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.ListOrdersUseCase;
import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto.CancelOrderRequest;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto.OrderResponse;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto.PageResponse;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Tag(name = "Pedidos")
@SecurityRequirement(name = "bearer")
class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PlaceOrderUseCase placeOrder;
    private final GetOrderUseCase getOrder;
    private final ListOrdersUseCase listOrders;
    private final CancelOrderUseCase cancelOrder;
    private final OrderWebMapper mapper;

    OrderController(PlaceOrderUseCase placeOrder, GetOrderUseCase getOrder, ListOrdersUseCase listOrders,
                    CancelOrderUseCase cancelOrder, OrderWebMapper mapper) {
        this.placeOrder = placeOrder;
        this.getOrder = getOrder;
        this.listOrders = listOrders;
        this.cancelOrder = cancelOrder;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Cria um pedido",
            description = "Repetir a requisição com a mesma Idempotency-Key e o mesmo corpo devolve o pedido original "
                    + "(header Idempotent-Replayed: true). Mesma chave com corpo diferente → 422.")
    @ApiResponse(responseCode = "201", description = "Pedido criado (ou repetição idempotente)")
    @ApiResponse(responseCode = "400", description = "Requisição inválida")
    @ApiResponse(responseCode = "422", description = "Regra de negócio violada ou Idempotency-Key reutilizada")
    ResponseEntity<OrderResponse> place(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Chave única gerada pelo cliente (ex.: UUID)", required = true)
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        var result = placeOrder.place(mapper.toCommand(request, AuthenticatedRequester.from(jwt), idempotencyKey));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.order().getId()).toUri();
        return ResponseEntity.created(location)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(mapper.toResponse(result.order()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um pedido", description = "Clientes só enxergam os próprios pedidos.")
    @ApiResponse(responseCode = "404", description = "Pedido inexistente ou de outro cliente")
    OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mapper.toResponse(getOrder.get(new OrderId(id), AuthenticatedRequester.from(jwt)));
    }

    @GetMapping
    @Operation(summary = "Lista pedidos", description = "Clientes veem os próprios pedidos; ADMIN vê todos.")
    PageResponse<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        var result = listOrders.list(AuthenticatedRequester.from(jwt), new PageQuery(page, size));
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela um pedido",
            description = "Se o pagamento já tiver sido aprovado, o payment-service fará o estorno.")
    @ApiResponse(responseCode = "422", description = "Pedido em estado que não permite cancelamento")
    OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                         @Valid @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request == null ? null : request.reason();
        return mapper.toResponse(cancelOrder.cancel(new OrderId(id), AuthenticatedRequester.from(jwt), reason));
    }
}
