package io.github.emanoeladolpho.order.application.port.in;

import io.github.emanoeladolpho.order.domain.model.CustomerId;

/** Quem está executando o caso de uso (vem do token, resolvido pelo adapter de entrada). */
public record Requester(CustomerId customerId, boolean admin) {

    public static Requester customer(String id) {
        return new Requester(new CustomerId(id), false);
    }

    public static Requester admin(String id) {
        return new Requester(new CustomerId(id), true);
    }
}
