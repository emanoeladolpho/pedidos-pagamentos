package io.github.emanoeladolpho.order.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, Long> {

    Optional<IdempotencyKeyJpaEntity> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
}
