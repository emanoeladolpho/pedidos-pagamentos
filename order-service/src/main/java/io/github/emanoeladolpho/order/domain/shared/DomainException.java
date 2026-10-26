package io.github.emanoeladolpho.order.domain.shared;

/** Violação de invariante ou regra de negócio. O código permite tratar cada caso nas bordas. */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
