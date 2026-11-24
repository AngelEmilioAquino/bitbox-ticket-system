package com.bitbox.gateway.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-side RabbitMQ topology: the gateway declares its own queue and binds it to inventory's exchange.
 * One shared queue (not one per instance) is enough: the cache lives in Redis and is shared, so whichever
 * gateway instance consumes the message invalidates the entry for all of them.
 */
@Configuration
public class MessagingConfig {

    /** Same names as in inventory-service: this is the messaging contract between both services. */
    public static final String INVENTORY_EXCHANGE = "inventory.events";
    public static final String AVAILABILITY_CHANGED_ROUTING_KEY = "inventory.availability.changed";
    public static final String AVAILABILITY_QUEUE = "gateway.availability-cache-invalidation";

    /** Declaring the exchange here too (idempotent) means the start order of the services does not matter. */
    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(INVENTORY_EXCHANGE, true, false);
    }

    @Bean
    public Queue availabilityQueue() {
        return QueueBuilder.durable(AVAILABILITY_QUEUE).build();
    }

    @Bean
    public Binding availabilityBinding(Queue availabilityQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(availabilityQueue).to(inventoryExchange).with(AVAILABILITY_CHANGED_ROUTING_KEY);
    }

    /**JSON converter. The producer stamps its own Java class name in the message headers; the consumer must have the same class on its classpath to deserialize it. This is a simple contract between both services.*/
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}