package io.github.emanoeladolpho.order.application.port.in;

import io.github.emanoeladolpho.order.domain.model.Order;

import java.math.BigDecimal;
import java.util.List;

public interface PlaceOrderUseCase {

    /**
     * Cria o pedido. Repetir a chamada com a mesma {@code idempotencyKey} e o mesmo conteúdo
     * devolve o pedido original ({@code replayed = true}) em vez de criar outro.
     */
    Result place(Command command);

    record Command(Requester requester, String idempotencyKey, String currency, List<Item> items) {

        public Command {
            items = List.copyOf(items);
        }
    }

    record Item(String sku, String productName, BigDecimal unitPrice, int quantity) {
    }

    record Result(Order order, boolean replayed) {
    }
}
