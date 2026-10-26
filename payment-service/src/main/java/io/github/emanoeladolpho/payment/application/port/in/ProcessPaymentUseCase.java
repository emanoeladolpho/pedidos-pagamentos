package io.github.emanoeladolpho.payment.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/** Processa o pagamento de um pedido recém-criado (disparado pelo evento OrderCreated). */
public interface ProcessPaymentUseCase {

    void process(Command command);

    record Command(UUID eventId, String orderId, String customerId, BigDecimal amount, String currency) {
    }
}
