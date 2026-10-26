package io.github.emanoeladolpho.order.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PageResult;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Leituras são transacionais para carregar os itens (lazy) durante o mapeamento para o domínio. */
@Component
@Transactional(readOnly = true)
class OrderPersistenceAdapter implements OrderRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final OrderJpaRepository repository;
    private final OrderPersistenceMapper mapper;

    OrderPersistenceAdapter(OrderJpaRepository repository, OrderPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Order order) {
        repository.save(mapper.toEntity(order));
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public PageResult<Order> findByCustomer(CustomerId customerId, PageQuery page) {
        return toResult(repository.findByCustomerId(customerId.value(), pageRequest(page)));
    }

    @Override
    public PageResult<Order> findAll(PageQuery page) {
        return toResult(repository.findAll(pageRequest(page)));
    }

    private static PageRequest pageRequest(PageQuery page) {
        return PageRequest.of(page.page(), page.size(), NEWEST_FIRST);
    }

    private PageResult<Order> toResult(Page<OrderJpaEntity> page) {
        return new PageResult<>(page.map(mapper::toDomain).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements());
    }
}
