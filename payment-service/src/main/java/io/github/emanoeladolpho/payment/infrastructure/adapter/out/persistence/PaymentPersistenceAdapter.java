package io.github.emanoeladolpho.payment.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository repository;
    private final PaymentPersistenceMapper mapper;

    PaymentPersistenceAdapter(PaymentJpaRepository repository, PaymentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Payment payment) {
        repository.save(mapper.toEntity(payment));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(OrderId orderId) {
        return repository.findByOrderId(orderId.value()).map(mapper::toDomain);
    }
}
