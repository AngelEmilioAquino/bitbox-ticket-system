package com.bitbox.inventory.stats;

import java.math.BigDecimal;

/** Interface projection for the native statistics query (column aliases match the getters). */
public interface EventStatsView {

    Long getEventId();

    String getEventName();

    Integer getTotalTickets();

    Integer getAvailableTickets();

    Long getTicketsSold();

    BigDecimal getOccupancyPercentage();

    Long getUniqueBuyers();

    Long getSalesRank();
}
