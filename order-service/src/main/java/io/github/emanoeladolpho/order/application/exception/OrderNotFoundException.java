package io.github.emanoeladolpho.order.application.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Object orderId) {
        super("Pedido não encontrado: " + orderId);
    }
}
