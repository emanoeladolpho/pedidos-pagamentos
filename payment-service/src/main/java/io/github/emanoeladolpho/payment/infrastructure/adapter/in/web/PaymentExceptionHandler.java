package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException;
import io.github.emanoeladolpho.payment.application.exception.PaymentNotFoundException;
import io.github.emanoeladolpho.payment.application.exception.RefundRejectedException;
import io.github.emanoeladolpho.payment.domain.shared.DomainException;
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
class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail handleNotFound(PaymentNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), "PAYMENT_NOT_FOUND");
    }

    @ExceptionHandler(RefundRejectedException.class)
    ProblemDetail handleRefundRejected(RefundRejectedException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "Estorno recusado", ex.getMessage(),
                "REFUND_REJECTED");
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    ProblemDetail handleGatewayUnavailable(PaymentGatewayUnavailableException ex) {
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "Gateway indisponível",
                "O provedor de pagamentos está indisponível. Tente novamente em instantes", "GATEWAY_UNAVAILABLE");
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage(),
                ex.getCode());
    }
}
