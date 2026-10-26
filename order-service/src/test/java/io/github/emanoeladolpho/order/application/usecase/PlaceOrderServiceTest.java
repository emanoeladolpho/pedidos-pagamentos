package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.DirectUnitOfWork;
import io.github.emanoeladolpho.order.application.exception.IdempotencyKeyReusedException;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase.Command;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase.Item;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase.Result;
import io.github.emanoeladolpho.order.application.port.in.Requester;
import io.github.emanoeladolpho.order.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.order.application.port.out.IdempotencyStore;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.domain.OrderFixtures;
import io.github.emanoeladolpho.order.domain.event.OrderCreated;
import io.github.emanoeladolpho.order.domain.model.Money;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.shared.DomainEvent;
import io.github.emanoeladolpho.order.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

    private static final Requester CUSTOMER = Requester.customer("customer-1");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private IdempotencyStore idempotencyStore;
    @Mock
    private DomainEventPublisher eventPublisher;

    private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();
    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        service = new PlaceOrderService(orderRepository, idempotencyStore, eventPublisher, unitOfWork);
    }

    private Command command(String key, String price) {
        return new Command(CUSTOMER, key, "BRL", List.of(
                new Item("SKU-1", "Teclado", new BigDecimal(price), 2),
                new Item("SKU-2", "Mouse", new BigDecimal("50.00"), 1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateOrderStoreKeyAndPublishEventInOneTransaction() {
        when(idempotencyStore.find(CUSTOMER.customerId(), "key-1")).thenReturn(Optional.empty());

        Result result = service.place(command("key-1", "100.00"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().getTotal()).isEqualTo(Money.of("250.00"));
        verify(orderRepository).save(result.order());

        ArgumentCaptor<IdempotencyStore.Entry> entry = ArgumentCaptor.forClass(IdempotencyStore.Entry.class);
        verify(idempotencyStore).save(entry.capture());
        assertThat(entry.getValue().orderId()).isEqualTo(result.order().getId());

        ArgumentCaptor<List<DomainEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publish(events.capture());
        assertThat(events.getValue()).singleElement().isInstanceOf(OrderCreated.class);
        assertThat(unitOfWork.transactions()).isEqualTo(1);
    }

    @Test
    void shouldReplayWhenSameKeyAndSamePayload() {
        Order existing = OrderFixtures.placedOrder();
        String hash = hashOf(command("key-1", "100.00"));
        when(idempotencyStore.find(CUSTOMER.customerId(), "key-1"))
                .thenReturn(Optional.of(new IdempotencyStore.Entry(CUSTOMER.customerId(), "key-1", hash, existing.getId())));
        when(orderRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        Result result = service.place(command("key-1", "100.0"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order()).isSameAs(existing);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectSameKeyWithDifferentPayload() {
        when(idempotencyStore.find(CUSTOMER.customerId(), "key-1"))
                .thenReturn(Optional.of(new IdempotencyStore.Entry(CUSTOMER.customerId(), "key-1",
                        hashOf(command("key-1", "100.00")), OrderFixtures.placedOrder().getId())));

        assertThatThrownBy(() -> service.place(command("key-1", "999.00")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void shouldPropagateDomainValidationWithoutSideEffects() {
        when(idempotencyStore.find(any(), any())).thenReturn(Optional.empty());
        Command invalid = new Command(CUSTOMER, "key-2", "BRL", List.of(new Item("sku inválido", "X", BigDecimal.ONE, 1)));

        assertThatThrownBy(() -> service.place(invalid)).isInstanceOf(DomainException.class);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(anyList());
    }

    private static String hashOf(Command command) {
        return PlaceOrderService.hash(command);
    }
}
