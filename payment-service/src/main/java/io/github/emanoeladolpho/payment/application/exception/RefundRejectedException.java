package io.github.emanoeladolpho.payment.application.exception;

public class RefundRejectedException extends RuntimeException {

    public RefundRejectedException(String reason) {
        super("O gateway recusou o estorno: " + reason);
    }
}
