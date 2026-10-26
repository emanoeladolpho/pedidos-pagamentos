package io.github.emanoeladolpho.payment.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.Money;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.emanoeladolpho.payment.domain.model.PaymentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PaymentPersistenceMapper {

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "orderId", source = "orderId.value")
    @Mapping(target = "customerId", source = "customerId.value")
    @Mapping(target = "amount", source = "amount.amount")
    @Mapping(target = "currency", source = "amount.currency")
    PaymentJpaEntity toEntity(Payment payment);

    default Payment toDomain(PaymentJpaEntity entity) {
        return Payment.restore(new PaymentId(entity.getId()), new OrderId(entity.getOrderId()),
                new CustomerId(entity.getCustomerId()), new Money(entity.getAmount(), entity.getCurrency()),
                PaymentStatus.valueOf(entity.getStatus()), entity.getGatewayTransactionId(),
                entity.getFailureReason(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
}
