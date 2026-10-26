package io.github.emanoeladolpho.chassis.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Tratamento de erros comum a todos os serviços (validação, concorrência, permissão e erro inesperado).
 * Cada serviço registra o próprio handler, com precedência maior, para as exceções do seu domínio.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class BaseExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /**
     * Lançada quando o método do controller tem restrições em parâmetros (header, path, query): nesse caso o
     * Spring valida também o corpo {@code @Valid} por aqui, e os erros de campo chegam dentro dos resultados.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> fieldError(
                        error instanceof FieldError fieldError
                                ? fieldError.getField()
                                : result.getMethodParameter().getParameterName(),
                        error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /**
     * Erros que o próprio Spring MVC trata (rota inexistente, método não suportado, JSON ilegível...) também
     * recebem code, timestamp e correlationId, para que todas as respostas de erro tenham o mesmo formato.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            ProblemDetails.enrich(problem, status == null ? "HTTP_" + statusCode.value() : status.name());
        }
        return response;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail body = ProblemDetails.of(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Um ou mais campos estão inválidos", "VALIDATION_ERROR");
        body.setProperty("errors", errors);
        return body;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O header '%s' é obrigatório".formatted(ex.getHeaderName()), "MISSING_HEADER");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "Requisição inválida", ex.getMessage(), "INVALID_ARGUMENT");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "Conflito",
                "O recurso foi alterado por outra operação. Consulte novamente e repita", "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetails.of(HttpStatus.CONFLICT, "Conflito",
                "A operação conflita com o estado atual dos dados", "CONFLICT");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para esta operação", "ACCESS_DENIED");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado", "INTERNAL_ERROR");
    }
}
