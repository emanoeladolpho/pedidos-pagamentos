package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simula a API HTTP de um provedor de pagamentos.
 * <p>
 * Valores "mágicos" para testes determinísticos (como os cartões de teste de provedores reais):
 * centavos <b>.13</b> → recusa; centavos <b>.66</b> → falha técnica.
 * Além disso, as taxas aleatórias configuradas simulam instabilidade.
 * <p>
 * Cobrança e estorno são idempotentes (pelo id do pagamento e pelo id da transação, respectivamente):
 * repetir a chamada devolve o resultado original, o que torna o retry seguro.
 */
public class SimulatedGatewayClient {

    public record ChargeResponse(boolean approved, String transactionId, String declineReason) {
    }

    private final SimulatedGatewayProperties properties;
    private final Map<String, ChargeResponse> charges = new ConcurrentHashMap<>();
    private final Map<String, ChargeResponse> refunds = new ConcurrentHashMap<>();

    public SimulatedGatewayClient(SimulatedGatewayProperties properties) {
        this.properties = properties;
    }

    public ChargeResponse charge(String idempotencyKey, BigDecimal amount) {
        ChargeResponse previous = charges.get(idempotencyKey);
        if (previous != null) {
            return previous;
        }
        simulateNetwork(amount);
        ChargeResponse response = cents(amount) == 13 || random() < properties.declineRate()
                ? new ChargeResponse(false, null, "Pagamento recusado pelo emissor")
                : new ChargeResponse(true, "gw-" + UUID.randomUUID(), null);
        charges.put(idempotencyKey, response);
        return response;
    }

    public ChargeResponse refund(String transactionId, BigDecimal amount) {
        ChargeResponse previous = refunds.get(transactionId);
        if (previous != null) {
            return previous;
        }
        simulateNetwork(amount);
        ChargeResponse response = new ChargeResponse(true, "rf-" + UUID.randomUUID(), null);
        refunds.put(transactionId, response);
        return response;
    }

    private void simulateNetwork(BigDecimal amount) {
        sleep(properties.latency().toMillis());
        if (cents(amount) == 66 || random() < properties.failureRate()) {
            throw new GatewayTechnicalException("Provedor indisponível (simulado)");
        }
    }

    private static int cents(BigDecimal amount) {
        return amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
    }

    private static double random() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayTechnicalException("Chamada interrompida");
        }
    }
}
