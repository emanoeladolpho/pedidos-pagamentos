package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

/** Erro técnico do provedor (equivalente a HTTP 5xx ou falha de rede). */
public class GatewayTechnicalException extends RuntimeException {

    public GatewayTechnicalException(String message) {
        super(message);
    }
}
