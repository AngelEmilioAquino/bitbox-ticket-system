package com.bitbox.inventory.event;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        LocalDateTime eventDate,
        Integer totalTickets,
        Integer availableTickets) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getEventDate(),
                event.getTotalTickets(), event.getAvailableTickets());
    }
}
