package io.github.emanoeladolpho.order.infrastructure.contract;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.github.emanoeladolpho.order.domain.OrderFixtures;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.infrastructure.adapter.out.messaging.OrderEventMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.emanoeladolpho.chassis.outbox.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Verificação de provedor: as mensagens que o order-service realmente produz (pelo mesmo mapper usado em
 * produção) precisam satisfazer os contratos publicados pelos consumidores em contracts/pacts.
 */
@Provider("order-service")
@PactFolder("../contracts/pacts")
class OrderEventsProviderPactTest {

    private final OrderEventMapper mapper = new OrderEventMapper(
            JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build(),
            "order-events");

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of()));
    }

    private static MessageAndMetadata toMessage(OutboxMessage message) {
        return new MessageAndMetadata(message.payload().getBytes(StandardCharsets.UTF_8),
                Map.of("contentType", "application/json"));
    }

    @PactVerifyProvider("an OrderCreated event")
    MessageAndMetadata orderCreated() {
        Order order = Order.place(OrderFixtures.CUSTOMER, List.of(OrderFixtures.item("SKU-1", "199.90", 1)));
        return toMessage(mapper.toMessage(order.pullDomainEvents().getFirst()));
    }

    @PactVerifyProvider("an OrderCancelled event")
    MessageAndMetadata orderCancelled() {
        Order order = OrderFixtures.placedOrder();
        order.cancel("Desistência");
        return toMessage(mapper.toMessage(order.pullDomainEvents().getFirst()));
    }
}
