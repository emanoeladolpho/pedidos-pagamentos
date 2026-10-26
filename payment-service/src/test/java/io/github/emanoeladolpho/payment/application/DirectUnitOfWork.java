package io.github.emanoeladolpho.payment.application;

import io.github.emanoeladolpho.payment.application.port.out.UnitOfWork;

import java.util.function.Supplier;

/** UnitOfWork de teste: executa o trabalho diretamente e conta quantas transações foram abertas. */
public class DirectUnitOfWork implements UnitOfWork {

    private int transactions;

    @Override
    public <T> T execute(Supplier<T> work) {
        transactions++;
        return work.get();
    }

    public int transactions() {
        return transactions;
    }
}
