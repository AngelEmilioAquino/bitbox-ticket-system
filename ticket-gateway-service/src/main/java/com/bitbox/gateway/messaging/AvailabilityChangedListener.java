package com.bitbox.gateway.messaging;

import java.time.Duration;

import com.bitbox.gateway.cache.AvailabilityCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Invalidates the cached availability of an event as soon as inventory-service reports a change,
 * so SSE subscribers see the new stock on their next tick instead of waiting for the TTL.
 */
@Component
public class AvailabilityChangedListener {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityChangedListener.class);
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(2);

    private final AvailabilityCache cache;

    public AvailabilityChangedListener(AvailabilityCache cache) {
        this.cache = cache;
    }

    /**
     * Runs on a RabbitMQ listener thread, not on a Netty event-loop thread, so waiting for Redis here
     * ({@code block}) does not stall the reactive HTTP server. Blocking lets the message be acknowledged
     * only after the eviction has really happened.
     */
    @RabbitListener(queues = MessagingConfig.AVAILABILITY_QUEUE)
    public void onAvailabilityChanged(AvailabilityChangedMessage message) {
        try {
            cache.evict(message.eventId()).block(REDIS_TIMEOUT);
            log.debug("Availability cache invalidated for event {} (available={})",
                    message.eventId(), message.availableTickets());
        } catch (RuntimeException ex) {
            log.warn("Could not invalidate availability cache for event {}: {}", message.eventId(), ex.getMessage());
        }
    }
}