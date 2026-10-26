package io.github.emanoeladolpho.order.infrastructure.contract;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase.Outcome;
import io.github.emanoeladolpho.order.infrastructure.adapter.in.messaging.PaymentEventTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de consumidor: o que o order-service espera das mensagens do payment-service.
 * Declara apenas os campos que o order-service lê (tolerant reader). O arquivo gerado em
 * contracts/pacts é verificado pelo payment-service no build dele.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class PaymentEventsConsumerPactTest {

    private static final String UUID_EXAMPLE = "3f1c9a52-8d4e-4b7a-9c2f-1a2b3c4d5e6f";

    private final PaymentEventTranslator translator = new PaymentEventTranslator(new ObjectMapper());

    private static MessagePact event(MessagePactBuilder builder, String description, String eventType,
                                     boolean withReason) {
        PactDslJsonBody data = new PactDslJsonBody()
                .uuid("paymentId", UUID_EXAMPLE)
                .uuid("orderId", UUID_EXAMPLE);
        if (withReason) {
            data.stringType("reason", "Pagamento recusado pelo emissor");
        }
        PactDslJsonBody body = new PactDslJsonBody()
                .uuid("eventId", UUID_EXAMPLE)
                .stringValue("eventType", eventType)
                .integerType("eventVersion", 1)
                .object("data", data);
        return builder.expectsToReceive(description)
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(body)
                .toPact();
    }

    @Pact(consumer = "order-service")
    MessagePact paymentInitiated(MessagePactBuilder builder) {
        return event(builder, "a PaymentInitiated event", "PaymentInitiated", false);
    }

    @Pact(consumer = "order-service")
    MessagePact paymentApproved(MessagePactBuilder builder) {
        return event(builder, "a PaymentApproved event", "PaymentApproved", false);
    }

    @Pact(consumer = "order-service")
    MessagePact paymentDeclined(MessagePactBuilder builder) {
        return event(builder, "a PaymentDeclined event", "PaymentDeclined", true);
    }

    @Pact(consumer = "order-service")
    MessagePact paymentRefunded(MessagePactBuilder builder) {
        return event(builder, "a PaymentRefunded event", "PaymentRefunded", true);
    }

    private Outcome translate(List<Message> messages) {
        return translator.translate(messages.getFirst().contentsAsString()).orElseThrow().outcome();
    }

    @Test
    @PactTestFor(pactMethod = "paymentInitiated")
    void shouldUnderstandPaymentInitiated(List<Message> messages) {
        assertThat(translate(messages)).isEqualTo(Outcome.INITIATED);
    }

    @Test
    @PactTestFor(pactMethod = "paymentApproved")
    void shouldUnderstandPaymentApproved(List<Message> messages) {
        assertThat(translate(messages)).isEqualTo(Outcome.APPROVED);
    }

    @Test
    @PactTestFor(pactMethod = "paymentDeclined")
    void shouldUnderstandPaymentDeclined(List<Message> messages) {
        var command = translator.translate(messages.getFirst().contentsAsString()).orElseThrow();
        assertThat(command.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(command.reason()).isEqualTo("Pagamento recusado pelo emissor");
    }

    @Test
    @PactTestFor(pactMethod = "paymentRefunded")
    void shouldUnderstandPaymentRefunded(List<Message> messages) {
        assertThat(translate(messages)).isEqualTo(Outcome.REFUNDED);
    }
}
