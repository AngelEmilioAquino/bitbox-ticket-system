package com.bitbox.inventory.sale;

/** In-process Spring event raised inside the purchase transaction. */
public record TicketPurchasedEvent(Long eventId, Integer availableTickets) {
}
