package io.github.emanoeladolpho.order.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.order.application.port.out.IdempotencyStore;
import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Chaves de idempotência com unique constraint (customer_id, idempotency_key): se duas requisições
 * com a mesma chave chegarem juntas, só uma transação é confirmada e a outra recebe 409.
 */
@Component
class IdempotencyStoreAdapter implements IdempotencyStore {

    private final IdempotencyKeyJpaRepository repository;

    IdempotencyStoreAdapter(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Entry> find(CustomerId customerId, String key) {
        return repository.findByCustomerIdAndIdempotencyKey(customerId.value(), key)
                .map(e -> new Entry(new CustomerId(e.getCustomerId()), e.getIdempotencyKey(), e.getRequestHash(),
                        new OrderId(e.getOrderId())));
    }

    @Override
    public void save(Entry entry) {
        repository.save(new IdempotencyKeyJpaEntity(entry.customerId().value(), entry.key(), entry.requestHash(),
                entry.orderId().value()));
    }
}
