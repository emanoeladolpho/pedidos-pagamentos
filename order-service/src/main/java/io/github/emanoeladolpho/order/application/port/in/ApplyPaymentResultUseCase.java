package io.github.emanoeladolpho.order.application.port.in;

import java.util.UUID;

/** Reage aos eventos publicados pelo payment-service, atualizando o status do pedido. */
public interface ApplyPaymentResultUseCase {

    void apply(Command command);

    enum Outcome { INITIATED, APPROVED, DECLINED, REFUNDED }

    /**
     * @param eventId identificador único do evento, usado para descartar entregas repetidas
     */
    record Command(UUID eventId, String orderId, Outcome outcome, String reason) {
    }
}
