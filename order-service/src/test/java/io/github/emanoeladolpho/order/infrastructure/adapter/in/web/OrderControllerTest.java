package io.github.emanoeladolpho.order.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.order.application.exception.IdempotencyKeyReusedException;
import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.application.port.in.CancelOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.GetOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.ListOrdersUseCase;
import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PageResult;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.domain.OrderFixtures;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import io.github.emanoeladolpho.order.domain.shared.DomainException;
import io.github.emanoeladolpho.order.infrastructure.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class, properties = "spring.config.name=order-service")
@Import({SecurityConfiguration.class, OrderWebMapperImpl.class})
class OrderControllerTest {

    private static final String BODY = """
            {"currency":"BRL","items":[{"sku":"SKU-1","productName":"Teclado","unitPrice":10.00,"quantity":2}]}
            """;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PlaceOrderUseCase placeOrder;
    @MockitoBean
    private GetOrderUseCase getOrder;
    @MockitoBean
    private ListOrdersUseCase listOrders;
    @MockitoBean
    private CancelOrderUseCase cancelOrder;

    private static JwtRequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("customer-1").claim("realm_access", Map.of("roles", List.of("USER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin-1").claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void placeShouldReturn201WithLocationAndPassTheAuthenticatedCustomer() throws Exception {
        Order order = OrderFixtures.placedOrder();
        when(placeOrder.place(any())).thenReturn(new PlaceOrderUseCase.Result(order, false));

        mvc.perform(post("/orders").with(user()).header("Idempotency-Key", "chave-123456")
                        .header("X-Correlation-Id", "corr-web-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/orders/" + order.getId()))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(header().string("X-Correlation-Id", "corr-web-1"))
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(25.50))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.items[0].subtotal").value(20.00));

        var captor = ArgumentCaptor.forClass(PlaceOrderUseCase.Command.class);
        verify(placeOrder).place(captor.capture());
        assertThat(captor.getValue().requester()).isEqualTo(Requester.customer("customer-1"));
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("chave-123456");
        assertThat(captor.getValue().items()).containsExactly(
                new PlaceOrderUseCase.Item("SKU-1", "Teclado", new BigDecimal("10.00"), 2));
    }

    @Test
    void replayedRequestShouldBeFlagged() throws Exception {
        when(placeOrder.place(any())).thenReturn(new PlaceOrderUseCase.Result(OrderFixtures.placedOrder(), true));

        mvc.perform(post("/orders").with(user()).header("Idempotency-Key", "chave-123456")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void placeWithoutIdempotencyKeyShouldReturn400() throws Exception {
        mvc.perform(post("/orders").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void placeWithMalformedIdempotencyKeyShouldReturn400() throws Exception {
        mvc.perform(post("/orders").with(user()).header("Idempotency-Key", "curta")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidBodyShouldReturn400WithFieldErrors() throws Exception {
        mvc.perform(post("/orders").with(user()).header("Idempotency-Key", "chave-123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"real","items":[{"sku":"x","productName":"","unitPrice":-1,"quantity":0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].sku')]").exists());
    }

    @Test
    void reusedKeyAndBusinessRuleViolationsShouldReturn422() throws Exception {
        when(placeOrder.place(any())).thenThrow(new IdempotencyKeyReusedException("chave-123456"))
                .thenThrow(new DomainException("DUPLICATE_SKU", "SKU repetido"));
        mvc.perform(post("/orders").with(user()).header("Idempotency-Key", "chave-123456")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mvc.perform(post("/orders").with(user()).header("Idempotency-Key", "chave-654321")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test
    void getShouldReturnOrderOr404() throws Exception {
        Order order = OrderFixtures.placedOrder();
        when(getOrder.get(order.getId(), Requester.admin("admin-1"))).thenReturn(order);
        when(getOrder.get(any(OrderId.class), eq(Requester.customer("customer-1"))))
                .thenThrow(new OrderNotFoundException(order.getId()));

        mvc.perform(get("/orders/{id}", order.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("customer-1"));
        mvc.perform(get("/orders/{id}", order.getId()).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void listShouldReturnPage() throws Exception {
        when(listOrders.list(Requester.customer("customer-1"), new PageQuery(1, 5)))
                .thenReturn(new PageResult<>(List.of(OrderFixtures.placedOrder()), 1, 5, 6));

        mvc.perform(get("/orders").param("page", "1").param("size", "5").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/orders").param("size", "500").with(user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelShouldAcceptOptionalReason() throws Exception {
        Order order = OrderFixtures.placedOrder();
        order.cancel("Desisti");
        when(cancelOrder.cancel(eq(order.getId()), any(), any())).thenReturn(order);

        mvc.perform(post("/orders/{id}/cancel", order.getId()).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Desisti\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Desisti"));
        mvc.perform(post("/orders/{id}/cancel", order.getId()).with(user()))
                .andExpect(status().isOk());

        verify(cancelOrder).cancel(order.getId(), Requester.customer("customer-1"), "Desisti");
        verify(cancelOrder).cancel(eq(order.getId()), eq(Requester.customer("customer-1")), isNull());
    }

    @Test
    void requestsWithoutTokenOrRoleShouldBeRejectedWithProblemDetails() throws Exception {
        mvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/orders").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
