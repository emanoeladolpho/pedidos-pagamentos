package io.github.emanoeladolpho.payment.infrastructure.contract;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import io.github.emanoeladolpho.payment.infrastructure.adapter.in.messaging.OrderEventTranslator;
import io.github.emanoeladolpho.payment.infrastructure.adapter.in.messaging.OrderEventTranslator.Translated;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de consumidor: o que o payment-service espera das mensagens do order-service.
 * Declara apenas os campos que o payment-service lê (tolerant reader). O arquivo gerado em
 * contracts/pacts é verificado pelo order-service no build dele.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "order-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class OrderEventsConsumerPactTest {

    private static final String UUID_EXAMPLE = "3f1c9a52-8d4e-4b7a-9c2f-1a2b3c4d5e6f";

    private final OrderEventTranslator translator = new OrderEventTranslator(new ObjectMapper());

    private static MessagePact event(MessagePactBuilder builder, String description, String eventType,
                                     PactDslJsonBody data) {
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

    @Pact(consumer = "payment-service")
    MessagePact orderCreated(MessagePactBuilder builder) {
        return event(builder, "an OrderCreated event", "OrderCreated", new PactDslJsonBody()
                .uuid("orderId", UUID_EXAMPLE)
                .stringType("customerId", "customer-1")
                .numberType("totalAmount", 199.90)
                .stringMatcher("currency", "[A-Z]{3}", "BRL"));
    }

    @Pact(consumer = "payment-service")
    MessagePact orderCancelled(MessagePactBuilder builder) {
        return event(builder, "an OrderCancelled event", "OrderCancelled", new PactDslJsonBody()
                .uuid("orderId", UUID_EXAMPLE)
                .stringType("reason", "Desistência"));
    }

    private Translated translate(List<Message> messages) {
        return translator.translate(messages.getFirst().contentsAsString()).orElseThrow();
    }

    @Test
    @PactTestFor(pactMethod = "orderCreated")
    void shouldTranslateOrderCreatedIntoPaymentProcessing(List<Message> messages) {
        Translated translated = translate(messages);

        assertThat(translated).isInstanceOf(Translated.Process.class);
        var command = ((Translated.Process) translated).command();
        assertThat(command.orderId()).isEqualTo(UUID_EXAMPLE);
        assertThat(command.amount()).isEqualByComparingTo("199.90");
        assertThat(command.currency()).isEqualTo("BRL");
    }

    @Test
    @PactTestFor(pactMethod = "orderCancelled")
    void shouldTranslateOrderCancelledIntoRefundCheck(List<Message> messages) {
        Translated translated = translate(messages);

        assertThat(translated).isInstanceOf(Translated.Cancel.class);
        assertThat(((Translated.Cancel) translated).command().reason()).isEqualTo("Desistência");
    }
}
