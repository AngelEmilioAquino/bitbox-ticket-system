package com.bitbox.inventory.stats;

import java.math.BigDecimal;

public record EventStatsResponse(
        Long eventId,
        String eventName,
        Integer totalTickets,
        Integer availableTickets,
        Long ticketsSold,
        BigDecimal occupancyPercentage,
        Long uniqueBuyers,
        Long salesRank) {

    static EventStatsResponse from(EventStatsView view) {
        return new EventStatsResponse(view.getEventId(), view.getEventName(), view.getTotalTickets(),
                view.getAvailableTickets(), view.getTicketsSold(), view.getOccupancyPercentage(),
                view.getUniqueBuyers(), view.getSalesRank());
    }
}
