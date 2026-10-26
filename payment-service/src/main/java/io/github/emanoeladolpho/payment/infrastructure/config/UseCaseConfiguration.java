package io.github.emanoeladolpho.payment.infrastructure.config;

import io.github.emanoeladolpho.payment.application.port.in.GetPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.HandleOrderCancelledUseCase;
import io.github.emanoeladolpho.payment.application.port.in.ProcessPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.in.RefundPaymentUseCase;
import io.github.emanoeladolpho.payment.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.payment.application.port.out.PaymentGateway;
import io.github.emanoeladolpho.payment.application.port.out.PaymentRepository;
import io.github.emanoeladolpho.payment.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.payment.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.payment.application.usecase.GetPaymentService;
import io.github.emanoeladolpho.payment.application.usecase.HandleOrderCancelledService;
import io.github.emanoeladolpho.payment.application.usecase.ProcessPaymentService;
import io.github.emanoeladolpho.payment.application.usecase.RefundPaymentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root: instancia os casos de uso (sem anotações do Spring) e liga as portas aos adapters. */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    ProcessPaymentUseCase processPaymentUseCase(PaymentRepository payments, PaymentGateway gateway,
                                                DomainEventPublisher publisher, ProcessedEventStore processedEvents,
                                                UnitOfWork unitOfWork) {
        return new ProcessPaymentService(payments, gateway, publisher, processedEvents, unitOfWork);
    }

    @Bean
    HandleOrderCancelledUseCase handleOrderCancelledUseCase(PaymentRepository payments, PaymentGateway gateway,
                                                            DomainEventPublisher publisher,
                                                            ProcessedEventStore processedEvents,
                                                            UnitOfWork unitOfWork) {
        return new HandleOrderCancelledService(payments, gateway, publisher, processedEvents, unitOfWork);
    }

    @Bean
    RefundPaymentUseCase refundPaymentUseCase(PaymentRepository payments, PaymentGateway gateway,
                                              DomainEventPublisher publisher, UnitOfWork unitOfWork) {
        return new RefundPaymentService(payments, gateway, publisher, unitOfWork);
    }

    @Bean
    GetPaymentUseCase getPaymentUseCase(PaymentRepository payments) {
        return new GetPaymentService(payments);
    }
}
