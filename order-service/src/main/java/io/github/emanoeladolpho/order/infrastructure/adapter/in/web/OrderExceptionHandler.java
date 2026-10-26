package io.github.emanoeladolpho.order.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.order.application.exception.IdempotencyKeyReusedException;
import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.domain.shared.DomainException;
import io.github.emanoeladolpho.chassis.web.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros específicos do serviço; os genéricos (validação, 500...) ficam no BaseExceptionHandler do chassis. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), "ORDER_NOT_FOUND");
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleKeyReused(IdempotencyKeyReusedException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key reutilizada", ex.getMessage(),
                "IDEMPOTENCY_KEY_REUSED");
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage(),
                ex.getCode());
    }
}
