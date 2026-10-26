package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException;
import io.github.emanoeladolpho.payment.application.exception.PaymentNotFoundException;
import io.github.emanoeladolpho.payment.application.exception.RefundRejectedException;
import io.github.emanoeladolpho.payment.application.port.in.GetPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.RefundPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.Requester;
import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.emanoeladolpho.payment.domain.shared.DomainException;
import io.github.emanoeladolpho.payment.infrastructure.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class, properties = "spring.config.name=payment-service")
@Import({SecurityConfiguration.class, PaymentWebMapperImpl.class})
class PaymentControllerTest {

    private static final String REFUND = "{\"reason\":\"Produto com defeito\"}";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private GetPaymentUseCase getPayment;
    @MockitoBean
    private RefundPaymentUseCase refundPayment;

    private static JwtRequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("customer-1").claim("realm_access", Map.of("roles", List.of("USER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin-1").claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.initiate(OrderId.of(UUID.randomUUID().toString()), new CustomerId("customer-1"),
                new Money(new BigDecimal("199.90"), "BRL"));
        payment.approve("gw-1");
        payment.pullDomainEvents();
        return payment;
    }

    @Test
    void getShouldReturnPaymentForOwner() throws Exception {
        Payment payment = approvedPayment();
        when(getPayment.get(payment.getId(), Requester.customer("customer-1"))).thenReturn(payment);

        mvc.perform(get("/payments/{id}", payment.getId()).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.orderId").value(payment.getOrderId().toString()))
                .andExpect(jsonPath("$.amount").value(199.90))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.gatewayTransactionId").value("gw-1"));
    }

    @Test
    void getByOrderShouldReturn404WhenMissing() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getPayment.getByOrder(new OrderId(orderId), Requester.admin("admin-1")))
                .thenThrow(new PaymentNotFoundException(orderId));

        mvc.perform(get("/payments").param("orderId", orderId.toString()).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void refundShouldBeAllowedOnlyForAdmin() throws Exception {
        Payment payment = approvedPayment();
        payment.refund("Produto com defeito");
        when(refundPayment.refund(payment.getId(), "Produto com defeito")).thenReturn(payment);

        mvc.perform(post("/payments/{id}/refund", payment.getId()).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content(REFUND))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/payments/{id}/refund", payment.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(REFUND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundRequiresReason() throws Exception {
        mvc.perform(post("/payments/{id}/refund", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyNoInteractions(refundPayment);
    }

    @Test
    void refundFailuresShouldBeMappedToProblemDetails() throws Exception {
        when(refundPayment.refund(any(PaymentId.class), eq("Produto com defeito")))
                .thenThrow(new DomainException("PAYMENT_NOT_REFUNDABLE", "Só pagamentos aprovados"))
                .thenThrow(new RefundRejectedException("limite excedido"))
                .thenThrow(new PaymentGatewayUnavailableException("fora", null));

        String id = UUID.randomUUID().toString();
        mvc.perform(post("/payments/{id}/refund", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(REFUND))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_REFUNDABLE"));
        mvc.perform(post("/payments/{id}/refund", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(REFUND))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_REJECTED"));
        mvc.perform(post("/payments/{id}/refund", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(REFUND))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GATEWAY_UNAVAILABLE"));
    }

    @Test
    void anonymousRequestShouldReturn401() throws Exception {
        mvc.perform(get("/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
