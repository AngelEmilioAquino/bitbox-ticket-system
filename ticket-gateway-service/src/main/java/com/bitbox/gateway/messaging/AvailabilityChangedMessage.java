package com.bitbox.gateway.messaging;

import java.time.Instant;

/** Message published by inventory-service when the stock of an event changes (gateway's own copy of the contract). */
public record AvailabilityChangedMessage(Long eventId, Integer availableTickets, Instant occurredAt) {
}