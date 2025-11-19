package com.lab.inventory.config;

import java.util.UUID;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String PRODUCT_UPDATE_EXCHANGE = "product.update.fanout";

    @Bean
    public FanoutExchange productUpdateExchange() {
        return new FanoutExchange(PRODUCT_UPDATE_EXCHANGE);
    }

    @Bean
    public Queue  myInstanceQueue() {
        return new Queue("product.updates." + UUID.randomUUID().toString(), false, true, true);
    }

    @Bean
    public Binding binding(Queue myInstanceQueue, FanoutExchange productUpdateExchange) {
        return BindingBuilder.bind(myInstanceQueue).to(productUpdateExchange);
    }
}
