package io.github.emanoeladolpho.payment;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class PaymentServiceApplication {

    /** Nome de configuração próprio (payment-service.yml) para não colidir com outros serviços no mesmo classpath. */
    public static final String CONFIG_NAME = "spring.config.name=payment-service";

    public static void main(String[] args) {
        new SpringApplicationBuilder(PaymentServiceApplication.class).properties(CONFIG_NAME).run(args);
    }
}
