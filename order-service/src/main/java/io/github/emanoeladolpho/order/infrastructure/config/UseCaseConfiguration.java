package io.github.emanoeladolpho.order.infrastructure.config;

import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase;
import io.github.emanoeladolpho.order.application.port.in.CancelOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.GetOrderUseCase;
import io.github.emanoeladolpho.order.application.port.in.ListOrdersUseCase;
import io.github.emanoeladolpho.order.application.port.in.PlaceOrderUseCase;
import io.github.emanoeladolpho.order.application.port.out.DomainEventPublisher;
import io.github.emanoeladolpho.order.application.port.out.IdempotencyStore;
import io.github.emanoeladolpho.order.application.port.out.OrderRepository;
import io.github.emanoeladolpho.order.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.order.application.port.out.UnitOfWork;
import io.github.emanoeladolpho.order.application.usecase.ApplyPaymentResultService;
import io.github.emanoeladolpho.order.application.usecase.CancelOrderService;
import io.github.emanoeladolpho.order.application.usecase.GetOrderService;
import io.github.emanoeladolpho.order.application.usecase.ListOrdersService;
import io.github.emanoeladolpho.order.application.usecase.PlaceOrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composição: os casos de uso não têm anotações do Spring; é a infraestrutura que os instancia
 * e liga às implementações das portas (adapters).
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    PlaceOrderUseCase placeOrderUseCase(OrderRepository orders, IdempotencyStore idempotencyStore,
                                        DomainEventPublisher publisher, UnitOfWork unitOfWork) {
        return new PlaceOrderService(orders, idempotencyStore, publisher, unitOfWork);
    }

    @Bean
    GetOrderUseCase getOrderUseCase(OrderRepository orders) {
        return new GetOrderService(orders);
    }

    @Bean
    ListOrdersUseCase listOrdersUseCase(OrderRepository orders) {
        return new ListOrdersService(orders);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(GetOrderUseCase getOrder, OrderRepository orders,
                                          DomainEventPublisher publisher, UnitOfWork unitOfWork) {
        return new CancelOrderService(getOrder, orders, publisher, unitOfWork);
    }

    @Bean
    ApplyPaymentResultUseCase applyPaymentResultUseCase(OrderRepository orders, ProcessedEventStore processedEvents,
                                                        UnitOfWork unitOfWork) {
        return new ApplyPaymentResultService(orders, processedEvents, unitOfWork);
    }
}
