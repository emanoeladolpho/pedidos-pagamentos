package io.github.emanoeladolpho.payment.infrastructure.adapter.out.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedGatewayClientTest {

    private static SimulatedGatewayClient client(double failureRate, double declineRate) {
        return new SimulatedGatewayClient(new SimulatedGatewayProperties(Duration.ZERO, failureRate, declineRate));
    }

    @Test
    void chargeShouldApproveByDefaultAndBeIdempotentByKey() {
        var gateway = client(0, 0);

        var first = gateway.charge("pay-1", new BigDecimal("10.00"));
        var repeated = gateway.charge("pay-1", new BigDecimal("10.00"));

        assertThat(first.approved()).isTrue();
        assertThat(first.transactionId()).startsWith("gw-");
        assertThat(repeated).isEqualTo(first);
        assertThat(gateway.charge("pay-2", new BigDecimal("10.00")).transactionId())
                .isNotEqualTo(first.transactionId());
    }

    @Test
    void magicCentsShouldForceDeclineOrTechnicalFailure() {
        var gateway = client(0, 0);

        assertThat(gateway.charge("pay-1", new BigDecimal("10.13")).approved()).isFalse();
        assertThatThrownBy(() -> gateway.charge("pay-2", new BigDecimal("10.66")))
                .isInstanceOf(GatewayTechnicalException.class);
    }

    @Test
    void configuredRatesShouldDriveRandomBehaviour() {
        assertThatThrownBy(() -> client(1, 0).charge("pay-1", BigDecimal.TEN))
                .isInstanceOf(GatewayTechnicalException.class);
        assertThat(client(0, 1).charge("pay-1", BigDecimal.TEN).declineReason())
                .isEqualTo("Pagamento recusado pelo emissor");
    }

    @Test
    void refundShouldBeIdempotentByTransaction() {
        var gateway = client(0, 0);

        var first = gateway.refund("gw-1", BigDecimal.TEN);

        assertThat(first.approved()).isTrue();
        assertThat(gateway.refund("gw-1", BigDecimal.TEN)).isEqualTo(first);
        assertThatThrownBy(() -> client(1, 0).refund("gw-2", BigDecimal.TEN))
                .isInstanceOf(GatewayTechnicalException.class);
    }
}
