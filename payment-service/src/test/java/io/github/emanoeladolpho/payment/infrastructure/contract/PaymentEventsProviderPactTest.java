package io.github.emanoeladolpho.payment.infrastructure.contract;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.shared.DomainEvent;
import io.github.emanoeladolpho.payment.infrastructure.adapter.out.messaging.PaymentEventMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verificação de provedor: as mensagens que o payment-service realmente produz (pelo mesmo mapper usado em
 * produção) precisam satisfazer os contratos publicados pelos consumidores em contracts/pacts.
 */
@Provider("payment-service")
@PactFolder("../contracts/pacts")
class PaymentEventsProviderPactTest {

    private final PaymentEventMapper mapper = new PaymentEventMapper(
            JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build(),
            "payment-events");

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of()));
    }

    private static Payment newPayment() {
        return Payment.initiate(OrderId.of(UUID.randomUUID().toString()), new CustomerId("customer-1"),
                new Money(new BigDecimal("199.90"), "BRL"));
    }

    private MessageAndMetadata toMessage(List<DomainEvent> events) {
        return new MessageAndMetadata(mapper.toMessage(events.getLast()).payload().getBytes(StandardCharsets.UTF_8),
                Map.of("contentType", "application/json"));
    }

    @PactVerifyProvider("a PaymentInitiated event")
    MessageAndMetadata paymentInitiated() {
        return toMessage(newPayment().pullDomainEvents());
    }

    @PactVerifyProvider("a PaymentApproved event")
    MessageAndMetadata paymentApproved() {
        Payment payment = newPayment();
        payment.approve("gw-123");
        return toMessage(payment.pullDomainEvents());
    }

    @PactVerifyProvider("a PaymentDeclined event")
    MessageAndMetadata paymentDeclined() {
        Payment payment = newPayment();
        payment.decline("Saldo insuficiente");
        return toMessage(payment.pullDomainEvents());
    }

    @PactVerifyProvider("a PaymentRefunded event")
    MessageAndMetadata paymentRefunded() {
        Payment payment = newPayment();
        payment.approve("gw-123");
        payment.refund("Pedido cancelado");
        return toMessage(payment.pullDomainEvents());
    }
}
