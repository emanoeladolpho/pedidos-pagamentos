package io.github.emanoeladolpho.order.application.port.out;

import java.util.function.Supplier;

/**
 * Fronteira transacional. A aplicação decide <b>o que</b> é atômico; a infraestrutura decide <b>como</b>
 * (ex.: transação JPA). Assim os casos de uso não dependem de anotações do Spring.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);

    default void run(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
