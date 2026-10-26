package io.github.emanoeladolpho.order.application.exception;

/** A mesma Idempotency-Key foi reutilizada com um conteúdo diferente do original. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("A Idempotency-Key '%s' já foi usada com um conteúdo diferente".formatted(key));
    }
}
