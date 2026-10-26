package io.github.emanoeladolpho.payment.infrastructure.adapter.in.messaging;

import io.github.emanoeladolpho.payment.application.port.in.HandleOrderCancelledUseCase;
import io.github.emanoeladolpho.payment.application.port.in.ProcessPaymentUseCase;
import io.github.emanoeladolpho.payment.infrastructure.adapter.in.messaging.OrderEventTranslator.Translated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderEventTranslator translator;
    private final ProcessPaymentUseCase processPayment;
    private final HandleOrderCancelledUseCase handleOrderCancelled;

    OrderEventsListener(OrderEventTranslator translator, ProcessPaymentUseCase processPayment,
                        HandleOrderCancelledUseCase handleOrderCancelled) {
        this.translator = translator;
        this.processPayment = processPayment;
        this.handleOrderCancelled = handleOrderCancelled;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "payment-service")
    void onOrderEvent(String message) {
        translator.translate(message).ifPresent(translated -> {
            switch (translated) {
                case Translated.Process process -> {
                    log.info("Processando pagamento do pedido {}", process.command().orderId());
                    processPayment.process(process.command());
                }
                case Translated.Cancel cancel -> {
                    log.info("Pedido {} cancelado: verificando estorno", cancel.command().orderId());
                    handleOrderCancelled.handle(cancel.command());
                }
            }
        });
    }
}
