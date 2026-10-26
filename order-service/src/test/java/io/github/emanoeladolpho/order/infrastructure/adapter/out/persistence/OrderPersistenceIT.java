package io.github.emanoeladolpho.order.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PageResult;
import io.github.emanoeladolpho.order.application.port.out.IdempotencyStore;
import io.github.emanoeladolpho.order.domain.OrderFixtures;
import io.github.emanoeladolpho.order.domain.model.CustomerId;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import io.github.emanoeladolpho.order.infrastructure.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.github.emanoeladolpho.order.domain.OrderFixtures.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa os adapters de persistência contra PostgreSQL real, com as migrations do Liquibase. */
@DataJpaTest(properties = "spring.config.name=order-service")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, OrderPersistenceAdapter.class, IdempotencyStoreAdapter.class,
        JpaUnitOfWork.class, OrderPersistenceMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderPersistenceIT {

    @Autowired
    private OrderPersistenceAdapter orders;
    @Autowired
    private IdempotencyStoreAdapter idempotencyStore;
    @Autowired
    private JpaUnitOfWork unitOfWork;

    @Test
    void shouldSaveAndRestoreFullAggregate() {
        Order order = OrderFixtures.placedOrder();
        unitOfWork.run(() -> orders.save(order));

        Order restored = orders.findById(order.getId()).orElseThrow();

        assertThat(restored.getCustomerId()).isEqualTo(order.getCustomerId());
        assertThat(restored.getTotal()).isEqualTo(Money.of("25.50"));
        assertThat(restored.getItems()).containsExactlyElementsOf(order.getItems());
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(restored.getVersion()).isZero();
    }

    @Test
    void shouldUpdateStatusAndIncrementVersion() {
        Order order = OrderFixtures.placedOrder();
        unitOfWork.run(() -> orders.save(order));

        unitOfWork.run(() -> {
            Order loaded = orders.findById(order.getId()).orElseThrow();
            loaded.markPaid();
            orders.save(loaded);
        });

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void staleCopyShouldFailWithOptimisticLock() {
        Order order = OrderFixtures.placedOrder();
        unitOfWork.run(() -> orders.save(order));
        Order first = orders.findById(order.getId()).orElseThrow();
        Order second = orders.findById(order.getId()).orElseThrow();

        first.markPaid();
        unitOfWork.run(() -> orders.save(first));
        second.cancel("concorrente");

        assertThatThrownBy(() -> unitOfWork.run(() -> orders.save(second)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void shouldPageByCustomerNewestFirst() {
        CustomerId customer = new CustomerId("pager-" + System.nanoTime());
        Order older = Order.place(customer, List.of(item("SKU-1", "1.00", 1)));
        Order newer = Order.place(customer, List.of(item("SKU-2", "2.00", 1)));
        unitOfWork.run(() -> {
            orders.save(older);
            orders.save(newer);
        });

        PageResult<Order> page = orders.findByCustomer(customer, new PageQuery(0, 1));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).singleElement().extracting(Order::getId).isEqualTo(newer.getId());
        assertThat(orders.findAll(new PageQuery(0, 100)).totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void idempotencyKeyShouldBeUniquePerCustomer() {
        Order order = OrderFixtures.placedOrder();
        var entry = new IdempotencyStore.Entry(order.getCustomerId(), "key-" + System.nanoTime(), "a".repeat(64),
                order.getId());
        unitOfWork.run(() -> {
            orders.save(order);
            idempotencyStore.save(entry);
        });

        assertThat(idempotencyStore.find(order.getCustomerId(), entry.key())).contains(entry);
        assertThat(idempotencyStore.find(new CustomerId("outro"), entry.key())).isEmpty();
        assertThatThrownBy(() -> unitOfWork.run(() -> idempotencyStore.save(entry)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
