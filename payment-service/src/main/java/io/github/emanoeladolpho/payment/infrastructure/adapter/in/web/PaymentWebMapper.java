package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.payment.domain.model.CustomerId;
import io.github.emanoeladolpho.payment.domain.model.OrderId;
import io.github.emanoeladolpho.payment.domain.model.Payment;
import io.github.emanoeladolpho.payment.domain.model.PaymentId;
import io.github.emanoeladolpho.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PaymentWebMapper {

    @Mapping(target = "currency", source = "amount.currency")
    @Mapping(target = "amount", source = "amount.amount")
    PaymentResponse toResponse(Payment payment);

    default String map(PaymentId id) {
        return id.toString();
    }

    default String map(OrderId id) {
        return id.toString();
    }

    default String map(CustomerId id) {
        return id.value();
    }
}
