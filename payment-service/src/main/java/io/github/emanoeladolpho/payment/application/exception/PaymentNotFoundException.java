package io.github.emanoeladolpho.payment.application.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Object id) {
        super("Pagamento não encontrado: " + id);
    }
}
