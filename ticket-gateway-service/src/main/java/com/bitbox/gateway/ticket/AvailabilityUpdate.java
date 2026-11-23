package com.bitbox.gateway.ticket;

import java.time.Instant;

import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;

/** Payload of each SSE "availability" event. */
public record AvailabilityUpdate(
        Long eventId,
        String eventName,
        Integer totalTickets,
        Integer availableTickets,
        Instant timestamp) {

    static AvailabilityUpdate from(EventResponse event, Instant now) {
        return new AvailabilityUpdate(event.id(), event.name(), event.totalTickets(), event.availableTickets(), now);
    }
}
