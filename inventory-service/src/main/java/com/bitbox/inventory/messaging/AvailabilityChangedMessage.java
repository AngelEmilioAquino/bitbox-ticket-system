package com.bitbox.inventory.messaging;

import java.time.Instant;

/** Message published to RabbitMQ every time the stock of an event changes. */
public record AvailabilityChangedMessage(Long eventId, Integer availableTickets, Instant occurredAt) {
}
