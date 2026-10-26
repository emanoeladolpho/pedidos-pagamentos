package io.github.emanoeladolpho.order.application.usecase;

import io.github.emanoeladolpho.order.application.DirectUnitOfWork;
import io.github.emanoeladolpho.order.application.exception.OrderNotFoundException;
import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase.Command;
import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase.Outcome;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.order.domain.OrderFixtures;
import io.github.emanoeladolpho.order.domain.model.Order;
import io.github.emanoeladolpho.order.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyPaymentResultServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProcessedEventStore processedEvents;

    private ApplyPaymentResultService service;

    @BeforeEach
    void setUp() {
        service = new ApplyPaymentResultService(orderRepository, processedEvents, new DirectUnitOfWork());
    }

    @ParameterizedTest
    @CsvSource({"INITIATED, PAYMENT_PENDING", "APPROVED, PAID", "DECLINED, FAILED"})
    void shouldApplyOutcomeAndMarkEventAsProcessed(Outcome outcome, OrderStatus expected) {
        Order order = OrderFixtures.placedOrder();
        UUID eventId = UUID.randomUUID();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        service.apply(new Command(eventId, order.getId().toString(), outcome, "motivo"));

        assertThat(order.getStatus()).isEqualTo(expected);
        verify(orderRepository).save(order);
        verify(processedEvents).markProcessed(eventId, ApplyPaymentResultService.CONSUMER);
    }

    @Test
    void refundShouldCancelPaidOrder() {
        Order order = OrderFixtures.placedOrder();
        order.markPaid();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        service.apply(new Command(UUID.randomUUID(), order.getId().toString(), Outcome.REFUNDED, "suporte"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void duplicatedEventShouldBeIgnored() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.isProcessed(eventId, ApplyPaymentResultService.CONSUMER)).thenReturn(true);

        service.apply(new Command(eventId, UUID.randomUUID().toString(), Outcome.APPROVED, null));

        verifyNoInteractions(orderRepository);
        verify(processedEvents, never()).markProcessed(any(), any());
    }

    @Test
    void unknownOrderShouldFailSoTheMessageCanBeRetried() {
        assertThatThrownBy(() -> service.apply(
                new Command(UUID.randomUUID(), UUID.randomUUID().toString(), Outcome.APPROVED, null)))
                .isInstanceOf(OrderNotFoundException.class);
        verify(processedEvents, never()).markProcessed(any(), any());
    }
}
