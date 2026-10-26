package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.DirectUnitOfWork;
import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.application.port.in.PageQuery;
import io.github.emanoeladolpho.order.application.port.in.PageResult;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.domain.OrderFixtures;
import io.github.emanoeladolpho.order.domain.event.OrderCancelled;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderId;
import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueriesAndCancelTest {

    private static final Requester OWNER = Requester.customer(OrderFixtures.CUSTOMER.value());
    private static final Requester STRANGER = Requester.customer("outro-cliente");
    private static final Requester ADMIN = Requester.admin("admin-1");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DomainEventPublisher eventPublisher;

    @Test
    void ownerAndAdminCanReadOrderButStrangerGetsNotFound() {
        Order order = OrderFixtures.placedOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        GetOrderService service = new GetOrderService(orderRepository);

        assertThat(service.get(order.getId(), OWNER)).isSameAs(order);
        assertThat(service.get(order.getId(), ADMIN)).isSameAs(order);
        assertThatThrownBy(() -> service.get(order.getId(), STRANGER)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void customerListsOwnOrdersAndAdminListsAll() {
        PageQuery page = new PageQuery(0, 20);
        PageResult<Order> mine = new PageResult<>(List.of(OrderFixtures.placedOrder()), 0, 20, 1);
        PageResult<Order> all = new PageResult<>(List.of(), 0, 20, 7);
        when(orderRepository.findByCustomer(OWNER.customerId(), page)).thenReturn(mine);
        when(orderRepository.findAll(page)).thenReturn(all);
        ListOrdersService service = new ListOrdersService(orderRepository);

        assertThat(service.list(OWNER, page)).isSameAs(mine);
        assertThat(service.list(ADMIN, page).totalElements()).isEqualTo(7);
    }

    @Test
    void cancelShouldPersistAndPublishOrderCancelled() {
        Order order = OrderFixtures.placedOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        DirectUnitOfWork unitOfWork = new DirectUnitOfWork();
        CancelOrderService service = new CancelOrderService(new GetOrderService(orderRepository), orderRepository,
                eventPublisher, unitOfWork);

        Order cancelled = service.cancel(order.getId(), OWNER, "Mudei de ideia");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(eventPublisher).publish(argThat(events ->
                events.size() == 1 && events.getFirst() instanceof OrderCancelled));
        assertThat(unitOfWork.transactions()).isEqualTo(1);
    }

    @Test
    void strangerCannotCancel() {
        Order order = OrderFixtures.placedOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        CancelOrderService service = new CancelOrderService(new GetOrderService(orderRepository), orderRepository,
                eventPublisher, new DirectUnitOfWork());

        assertThatThrownBy(() -> service.cancel(order.getId(), STRANGER, null))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void pageHelpers() {
        PageResult<Integer> page = new PageResult<>(List.of(1, 2), 0, 2, 5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.map(String::valueOf).content()).containsExactly("1", "2");
        assertThat(new PageResult<>(List.of(), 0, 0, 0).totalPages()).isZero();
        assertThatThrownBy(() -> new PageQuery(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(OrderId.of(OrderId.newId().toString())).isNotNull();
    }
}
