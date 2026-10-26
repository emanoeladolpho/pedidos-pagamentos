package io.github.emanoeladolpho.order;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class OrderServiceApplication {

    /** Nome de configuração próprio (order-service.yml) para não colidir com outros serviços no mesmo classpath. */
    public static final String CONFIG_NAME = "spring.config.name=order-service";

    public static void main(String[] args) {
        new SpringApplicationBuilder(OrderServiceApplication.class).properties(CONFIG_NAME).run(args);
    }
}
