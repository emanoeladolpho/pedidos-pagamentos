package io.github.emanoeladolpho.payment.application.usecase;

import io.github.emanoeladolpho.payment.application.DirectUnitOfWork;
import io.github.emanoeladolpho.payment.application.exception.PaymentNotFoundException;
import io.github.emanoeladolpho.payment.application.exception.RefundRejectedException;
import io.github.emanoeladolpho.payment.application.port.in.HandleOrderCancelledUseCase;
import io.github.emanoeladolpho.payment.application.port.in.ProcessPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.Requester;
import io.github.emanoeladolpho.payment.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway.Result.Approved;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway.Result.Declined;
import io.github.emanoeladolpho.payment.application.exception.PaymentGatewayUnavailableException;
import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.payment.domain.event.PaymentApproved;
import io.github.emanoeladolpho.payment.domain.event.PaymentDeclined;
import io.github.emanoeladolpho.payment.domain.event.PaymentInitiated;
import io.github.emanoeladolpho.payment.domain.event.PaymentRefunded;
import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;
import io.github.emanoeladolpho.payment.domain.shared.DomainEvent;
import io.github.emanoeladolpho.payment.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentUseCasesTest {

    private static final String ORDER = UUID.randomUUID().toString();

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private DomainEventPublisher publisher;
    @Mock
    private ProcessedEventStore processedEvents;

    private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();
    private final List<DomainEvent> published = new ArrayList<>();
    /** Repositório "em memória" sobre o mock: guarda o último pagamento salvo. */
    private Payment stored;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> published.addAll(inv.getArgument(0))).when(publisher).publish(any());
        lenient().doAnswer(inv -> stored = inv.getArgument(0)).when(repository).save(any());
        lenient().when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored));
    }

    private Payment approvedPayment() {
        Payment payment = Payment.initiate(OrderId.of(ORDER), new CustomerId("c1"), Money.of("80.00"));
        payment.approve("gw-1");
        payment.pullDomainEvents();
        stored = payment;
        return payment;
    }

    @Nested
    class Process {

        private ProcessPaymentService service;
        private final ProcessPaymentUseCase.Command command = new ProcessPaymentUseCase.Command(
                UUID.randomUUID(), ORDER, "c1", new BigDecimal("80.00"), "BRL");

        @BeforeEach
        void setUp() {
            service = new ProcessPaymentService(repository, gateway, publisher, processedEvents, unitOfWork);
        }

        @Test
        void approvedChargeShouldPublishInitiatedAndApprovedInSeparateTransactions() {
            when(repository.findByOrderId(OrderId.of(ORDER))).thenReturn(Optional.empty());
            when(gateway.charge(any(), any(), any())).thenReturn(new Approved("gw-123"));

            service.process(command);

            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(published).hasExactlyElementsOfTypes(PaymentInitiated.class, PaymentApproved.class);
            assertThat(unitOfWork.transactions()).isEqualTo(2);
            verify(processedEvents).markProcessed(command.eventId(), ProcessPaymentService.CONSUMER);
        }

        @Test
        void declinedChargeShouldPublishDeclined() {
            when(repository.findByOrderId(any())).thenReturn(Optional.empty());
            when(gateway.charge(any(), any(), any())).thenReturn(new Declined("Saldo insuficiente"));

            service.process(command);

            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.DECLINED);
            assertThat(published).hasExactlyElementsOfTypes(PaymentInitiated.class, PaymentDeclined.class);
        }

        @Test
        void gatewayFailureShouldPropagateWithoutMarkingEventSoItIsRetried() {
            when(repository.findByOrderId(any())).thenReturn(Optional.empty());
            when(gateway.charge(any(), any(), any()))
                    .thenThrow(new PaymentGatewayUnavailableException("fora do ar", null));

            assertThatThrownBy(() -> service.process(command)).isInstanceOf(PaymentGatewayUnavailableException.class);

            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(processedEvents, never()).markProcessed(any(), any());
        }

        @Test
        void retryShouldReuseExistingPendingPaymentInsteadOfCreatingAnother() {
            Payment pending = Payment.initiate(OrderId.of(ORDER), new CustomerId("c1"), Money.of("80.00"));
            pending.pullDomainEvents();
            stored = pending;
            when(repository.findByOrderId(any())).thenReturn(Optional.of(pending));
            when(gateway.charge(any(), any(), any())).thenReturn(new Approved("gw-9"));

            service.process(command);

            assertThat(published).hasExactlyElementsOfTypes(PaymentApproved.class);
        }

        @Test
        void alreadyProcessedEventShouldBeIgnored() {
            when(processedEvents.isProcessed(command.eventId(), ProcessPaymentService.CONSUMER)).thenReturn(true);

            service.process(command);

            verifyNoInteractions(gateway, repository);
        }

        @Test
        void finalizedPaymentShouldNotBeChargedAgain() {
            when(repository.findByOrderId(any())).thenReturn(Optional.of(approvedPayment()));

            service.process(command);

            verifyNoInteractions(gateway);
            verify(processedEvents).markProcessed(command.eventId(), ProcessPaymentService.CONSUMER);
        }
    }

    @Nested
    class OrderCancelled {

        private HandleOrderCancelledService service;
        private final HandleOrderCancelledUseCase.Command command =
                new HandleOrderCancelledUseCase.Command(UUID.randomUUID(), ORDER, "Cliente desistiu");

        @BeforeEach
        void setUp() {
            service = new HandleOrderCancelledService(repository, gateway, publisher, processedEvents, unitOfWork);
        }

        @Test
        void approvedPaymentShouldBeRefunded() {
            when(repository.findByOrderId(any())).thenReturn(Optional.of(approvedPayment()));
            when(gateway.refund(any(), any(), any())).thenReturn(new Approved("rf-1"));

            service.handle(command);

            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(published).singleElement().isInstanceOf(PaymentRefunded.class);
            verify(processedEvents).markProcessed(command.eventId(), HandleOrderCancelledService.CONSUMER);
        }

        @Test
        void pendingPaymentShouldBeCancelledWithoutCallingGateway() {
            Payment pending = Payment.initiate(OrderId.of(ORDER), new CustomerId("c1"), Money.of("80.00"));
            when(repository.findByOrderId(any())).thenReturn(Optional.of(pending));

            service.handle(command);

            assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verifyNoInteractions(gateway);
        }

        @Test
        void noPaymentShouldOnlyMarkEvent() {
            when(repository.findByOrderId(any())).thenReturn(Optional.empty());

            service.handle(command);

            verifyNoInteractions(gateway);
            verify(processedEvents).markProcessed(command.eventId(), HandleOrderCancelledService.CONSUMER);
        }

        @Test
        void duplicatedEventIsIgnored() {
            when(processedEvents.isProcessed(command.eventId(), HandleOrderCancelledService.CONSUMER)).thenReturn(true);

            service.handle(command);

            verifyNoInteractions(repository, gateway);
        }

        @Test
        void rejectedRefundShouldFailForRetry() {
            when(repository.findByOrderId(any())).thenReturn(Optional.of(approvedPayment()));
            when(gateway.refund(any(), any(), any())).thenReturn(new Declined("prazo expirado"));

            assertThatThrownBy(() -> service.handle(command)).isInstanceOf(RefundRejectedException.class);
            verify(processedEvents, never()).markProcessed(any(), any());
        }
    }

    @Nested
    class ManualRefundAndQueries {

        @Test
        void adminRefundShouldRefundApprovedPayment() {
            Payment payment = approvedPayment();
            when(gateway.refund(any(), any(), any())).thenReturn(new Approved("rf-2"));

            Payment refunded = new RefundPaymentService(repository, gateway, publisher, unitOfWork)
                    .refund(payment.getId(), "Suporte");

            assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(published).singleElement().isInstanceOf(PaymentRefunded.class);
        }

        @Test
        void refundShouldFailForUnknownOrNotApprovedOrDeclinedByGateway() {
            RefundPaymentService service = new RefundPaymentService(repository, gateway, publisher, unitOfWork);
            assertThatThrownBy(() -> service.refund(io.github.emanoeladolpho.payment.domain.model.PaymentId.newId(), "x"))
                    .isInstanceOf(PaymentNotFoundException.class);

            Payment pending = Payment.initiate(OrderId.of(ORDER), new CustomerId("c1"), Money.of("1.00"));
            stored = pending;
            assertThatThrownBy(() -> service.refund(pending.getId(), "x")).isInstanceOf(DomainException.class);

            Payment approved = approvedPayment();
            when(gateway.refund(any(), any(), any())).thenReturn(new Declined("não permitido"));
            assertThatThrownBy(() -> service.refund(approved.getId(), "x")).isInstanceOf(RefundRejectedException.class);
        }

        @Test
        void onlyOwnerOrAdminCanSeePayment() {
            Payment payment = approvedPayment();
            when(repository.findByOrderId(payment.getOrderId())).thenReturn(Optional.of(payment));
            GetPaymentService service = new GetPaymentService(repository);

            assertThat(service.get(payment.getId(), Requester.customer("c1"))).isSameAs(payment);
            assertThat(service.getByOrder(payment.getOrderId(), Requester.admin("adm"))).isSameAs(payment);
            assertThatThrownBy(() -> service.get(payment.getId(), Requester.customer("outro")))
                    .isInstanceOf(PaymentNotFoundException.class);
            assertThatThrownBy(() -> service.getByOrder(payment.getOrderId(), Requester.customer("outro")))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }
}
