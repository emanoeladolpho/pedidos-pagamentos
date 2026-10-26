package io.github.emanoeladolpho.payment.application.exception;

/** Falha técnica no gateway: a operação pode ser tentada novamente mais tarde. */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
