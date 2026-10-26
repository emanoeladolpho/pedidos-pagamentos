package io.github.emanoeladolpho.payment.infrastructure.adapter.in.messaging;

import io.github.emanoeladolpho.payment.application.port.in.HandleOrderCancelledUseCase;
import io.github.emanoeladolpho.payment.application.port.in.ProcessPaymentUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.emanoeladolpho.chassis.messaging.MalformedEventException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Converte as mensagens do order-service em comandos. Lê somente os campos de que precisa
 * (tolerant reader): campos novos no evento não quebram este consumidor.
 */
@Component
public class OrderEventTranslator {

    /** Comando resultante da tradução: pagamento a processar ou cancelamento a tratar. */
    public sealed interface Translated permits Translated.Process, Translated.Cancel {

        record Process(ProcessPaymentUseCase.Command command) implements Translated {
        }

        record Cancel(HandleOrderCancelledUseCase.Command command) implements Translated {
        }
    }

    private final ObjectMapper objectMapper;

    public OrderEventTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<Translated> translate(String json) {
        try {
            JsonNode envelope = objectMapper.readTree(json);
            String eventType = envelope.path("eventType").asText();
            if (!eventType.equals("OrderCreated") && !eventType.equals("OrderCancelled")) {
                return Optional.empty();
            }
            UUID eventId = UUID.fromString(envelope.required("eventId").asText());
            JsonNode data = envelope.required("data");
            String orderId = UUID.fromString(data.required("orderId").asText()).toString();
            if (eventType.equals("OrderCreated")) {
                return Optional.of(new Translated.Process(new ProcessPaymentUseCase.Command(eventId, orderId,
                        data.required("customerId").asText(), data.required("totalAmount").decimalValue(),
                        data.required("currency").asText())));
            }
            String reason = data.hasNonNull("reason") ? data.get("reason").asText() : null;
            return Optional.of(new Translated.Cancel(new HandleOrderCancelledUseCase.Command(eventId, orderId, reason)));
        } catch (Exception e) {
            throw new MalformedEventException("Evento de pedido inválido: " + e.getMessage(), e);
        }
    }
}
