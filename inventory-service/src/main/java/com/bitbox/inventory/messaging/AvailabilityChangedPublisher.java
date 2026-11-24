package com.bitbox.inventory.messaging;

import java.time.Clock;

import com.bitbox.inventory.sale.TicketPurchasedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 AFTER_COMMIT is the key: the message is sent only once the sale is really stored.
 * A rolled-back purchase (sold out, error...) never produces a message, so consumers never react to
 * something that did not happen.
 */
@Component
public class AvailabilityChangedPublisher {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityChangedPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public AvailabilityChangedPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketPurchased(TicketPurchasedEvent event) {
        AvailabilityChangedMessage message =
                new AvailabilityChangedMessage(event.eventId(), event.availableTickets(), clock.instant());
        try {
            rabbitTemplate.convertAndSend(MessagingConfig.INVENTORY_EXCHANGE,
                    MessagingConfig.AVAILABILITY_CHANGED_ROUTING_KEY, message);
        } catch (AmqpException ex) {
            log.warn("Could not publish availability change for event {}: {}", event.eventId(), ex.getMessage());
        }
    }
}