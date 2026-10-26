package io.github.emanoeladolpho.chassis.correlation;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Identificador que acompanha uma operação de negócio por todos os serviços:
 * requisição HTTP → outbox → mensagem Kafka → outro serviço → próxima mensagem.
 * Fica no MDC, então aparece automaticamente em todas as linhas de log (JSON).
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    public static String currentOrNew() {
        return current().orElseGet(CorrelationId::newId);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** Aceita apenas valores seguros para log (evita injeção de quebras de linha e valores gigantes). */
    public static String sanitize(String candidate) {
        if (candidate == null || !candidate.matches("[A-Za-z0-9._-]{1,64}")) {
            return newId();
        }
        return candidate;
    }

    public static void set(String value) {
        MDC.put(MDC_KEY, value);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
