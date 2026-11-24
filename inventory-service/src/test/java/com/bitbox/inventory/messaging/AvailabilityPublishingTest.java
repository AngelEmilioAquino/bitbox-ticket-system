package com.bitbox.inventory.messaging;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.sale.PurchaseExceptions.SoldOutException;
import com.bitbox.inventory.sale.PurchaseService;
import com.bitbox.inventory.support.IntegrationTest;
import com.bitbox.inventory.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/** A committed purchase publishes exactly one message to RabbitMQ; a failed one publishes nothing. */
class AvailabilityPublishingTest extends IntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 5_000;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TopicExchange inventoryExchange;

    @Autowired
    private JsonMapper jsonMapper;

    /** Plays the role of a consumer (like the gateway) with its own temporary queue. */
    private Queue testQueue;

    @BeforeEach
    void bindTestQueue() {
        // Durable (RabbitMQ 4 rejects transient non-exclusive queues) and not auto-delete (an auto-delete queue
        // disappears as soon as its first consumer disconnects). Deleted explicitly after each test.
        testQueue = new Queue("test.availability." + UUID.randomUUID(), true, false, false);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(inventoryExchange)
                .with(MessagingConfig.AVAILABILITY_CHANGED_ROUTING_KEY));
    }

    @AfterEach
    void deleteTestQueue() {
        amqpAdmin.deleteQueue(testQueue.getName());
    }

    @Test
    void committedPurchasePublishesNewAvailability() {
        User user = createUser("ana@bitbox.com");
        Event event = createEvent("Concierto", 5);

        purchaseService.purchase(event.getId(), user.getId());

        Message raw = rabbitTemplate.receive(testQueue.getName(), RECEIVE_TIMEOUT_MS);
        assertThat(raw).as("message published after commit").isNotNull();
        assertThat(raw.getMessageProperties().getContentType()).isEqualTo("application/json");

        // Read the JSON body as any consumer would (the consumer does not need our Java class)
        AvailabilityChangedMessage message = jsonMapper.readValue(raw.getBody(), AvailabilityChangedMessage.class);
        assertThat(message.eventId()).isEqualTo(event.getId());
        assertThat(message.availableTickets()).isEqualTo(4);
        assertThat(message.occurredAt()).isNotNull();
    }

    @Test
    void soldOutPurchaseRollsBackAndPublishesNothing() {
        User user = createUser("ana@bitbox.com");
        Event event = createEvent("Concierto", 1);
        purchaseService.purchase(event.getId(), user.getId());
        rabbitTemplate.receive(testQueue.getName(), RECEIVE_TIMEOUT_MS); // consume the successful one

        assertThatThrownBy(() -> purchaseService.purchase(event.getId(), user.getId()))
                .isInstanceOf(SoldOutException.class);

        assertThat(rabbitTemplate.receive(testQueue.getName(), 1_000)).isNull();
    }
}