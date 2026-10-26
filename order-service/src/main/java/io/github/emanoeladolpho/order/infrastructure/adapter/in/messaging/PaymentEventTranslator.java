package io.github.emanoeladolpho.order.infrastructure.adapter.in.messaging;

import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase.Command;
import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase.Outcome;
import io.github.emanoeladolpho.chassis.messaging.MalformedEventException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lê as mensagens do payment-service e as converte em comandos. Tipos desconhecidos são ignorados
 * (o produtor pode publicar novos eventos sem quebrar este consumidor); mensagens inválidas viram
 * {@link MalformedEventException} e vão para a DLT.
 */
@Component
public class PaymentEventTranslator {

    private static final Map<String, Outcome> OUTCOMES = Map.of(
            "PaymentInitiated", Outcome.INITIATED,
            "PaymentApproved", Outcome.APPROVED,
            "PaymentDeclined", Outcome.DECLINED,
            "PaymentRefunded", Outcome.REFUNDED);

    private final ObjectMapper objectMapper;

    public PaymentEventTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<Command> translate(String json) {
        try {
            JsonNode envelope = objectMapper.readTree(json);
            Outcome outcome = OUTCOMES.get(envelope.path("eventType").asText());
            if (outcome == null) {
                return Optional.empty();
            }
            JsonNode data = envelope.required("data");
            String reason = data.hasNonNull("reason") ? data.get("reason").asText() : null;
            return Optional.of(new Command(UUID.fromString(envelope.required("eventId").asText()),
                    UUID.fromString(data.required("orderId").asText()).toString(), outcome, reason));
        } catch (Exception e) {
            throw new MalformedEventException("Evento de pagamento inválido: " + e.getMessage(), e);
        }
    }
}
