package io.github.emanoeladolpho.order.infrastructure.adapter.in.messaging;

import io.github.emanoeladolpho.order.application.port.in.ApplyPaymentResultUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final PaymentEventTranslator translator;
    private final ApplyPaymentResultUseCase applyPaymentResult;

    PaymentEventsListener(PaymentEventTranslator translator, ApplyPaymentResultUseCase applyPaymentResult) {
        this.translator = translator;
        this.applyPaymentResult = applyPaymentResult;
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "order-service")
    void onPaymentEvent(String message) {
        translator.translate(message).ifPresentOrElse(command -> {
            log.info("Evento de pagamento recebido: outcome={} orderId={}", command.outcome(), command.orderId());
            applyPaymentResult.apply(command);
        }, () -> log.debug("Evento de pagamento ignorado (tipo não tratado)"));
    }
}
