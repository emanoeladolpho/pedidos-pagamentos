package io.github.emanoeladolpho.order.application.port.in;

public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page deve ser >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size deve estar entre 1 e " + MAX_SIZE);
        }
    }
}
