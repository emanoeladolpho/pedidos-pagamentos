package io.github.emanoeladolpho.payment.application.port.in;

import java.util.UUID;

/** Reage ao cancelamento do pedido: estorna se já aprovado, ou cancela o pagamento pendente. */
public interface HandleOrderCancelledUseCase {

    void handle(Command command);

    record Command(UUID eventId, String orderId, String reason) {
    }
}
