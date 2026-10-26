package io.github.emanoeladolpho.chassis.web;

import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/** Fábrica de respostas de erro no padrão RFC 7807/9457, com código estável e correlation id. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return enrich(problem, code);
    }

    /** Acrescenta os campos padronizados (code, timestamp, correlationId) a um ProblemDetail existente. */
    public static ProblemDetail enrich(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));
        return problem;
    }
}
