package com.bitbox.inventory.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by inventory-service: a topic exchange where inventory changes are published.
 * Consumers declare and bind their own queues to it.
 */
@Configuration
public class MessagingConfig {

    public static final String INVENTORY_EXCHANGE = "inventory-exchange";
    public static final String AVAILABILITY_CHANGED_ROUTING_KEY = "inventory.availability.changed";

    /** Durable exchange: survives a broker restart. Declared automatically by RabbitAdmin on first connection. */
    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(INVENTORY_EXCHANGE, true, false);
    }

    /** Messages travel as JSON instead of Java serialization. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }


}
