package io.github.emanoeladolpho.payment.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.payment.application.port.out.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Implementa a fronteira transacional da aplicação com uma transação JPA (TransactionTemplate). */
@Component
class JpaUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    JpaUnitOfWork(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
