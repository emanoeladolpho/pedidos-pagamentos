package io.github.emanoeladolpho.order.infrastructure.adapter.out.messaging;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contrato público (versão 1) dos eventos publicados no tópico de pedidos. É deliberadamente separado
 * dos eventos de domínio: o domínio pode evoluir sem quebrar consumidores; mudanças aqui são versionadas
 * e verificadas pelos testes de contrato (Pact).
 */
public final class OrderEventMessages {

    public static final int VERSION = 1;

    private OrderEventMessages() {
    }

    public record OrderCreatedV1(String orderId, String customerId, BigDecimal totalAmount, String currency,
                                 List<Item> items) {

        public record Item(String sku, String productName, BigDecimal unitPrice, int quantity) {
        }
    }

    public record OrderCancelledV1(String orderId, String customerId, String previousStatus, String reason) {
    }
}
