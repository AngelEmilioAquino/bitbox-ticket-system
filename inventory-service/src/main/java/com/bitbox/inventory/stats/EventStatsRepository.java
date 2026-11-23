package com.bitbox.inventory.stats;

import java.util.List;

import com.bitbox.inventory.event.Event;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface EventStatsRepository extends Repository<Event, Long> {

    /**
     * Per-event sales statistics in a single round trip.
     * <ul>
     *   <li>CTE {@code sales_per_event}: joins ticket_sales with users and aggregates sold tickets
     *       ({@code COUNT}) and unique buyers ({@code COUNT(DISTINCT)}) per event.</li>
     *   <li>{@code LEFT JOIN} from events so events without sales still appear (with zeros).</li>
     *   <li>{@code NULLIF} guards the occupancy division.</li>
     *   <li>{@code RANK() OVER} ranks events by tickets sold (ties share the same rank).</li>
     * </ul>
     * Aliases are double-quoted so PostgreSQL keeps their camelCase for the projection.
     */
    @Query(nativeQuery = true, value = """
            WITH sales_per_event AS (
                SELECT ts.event_id,
                       COUNT(ts.id)         AS tickets_sold,
                       COUNT(DISTINCT u.id) AS unique_buyers
                FROM ticket_sales ts
                JOIN users u ON u.id = ts.user_id
                GROUP BY ts.event_id
            )
            SELECT e.id                                   AS "eventId",
                   e.name                                 AS "eventName",
                   e.total_tickets                        AS "totalTickets",
                   e.available_tickets                    AS "availableTickets",
                   COALESCE(s.tickets_sold, 0)            AS "ticketsSold",
                   ROUND(COALESCE(s.tickets_sold, 0) * 100.0
                         / NULLIF(e.total_tickets, 0), 2) AS "occupancyPercentage",
                   COALESCE(s.unique_buyers, 0)           AS "uniqueBuyers",
                   RANK() OVER (ORDER BY COALESCE(s.tickets_sold, 0) DESC) AS "salesRank"
            FROM events e
            LEFT JOIN sales_per_event s ON s.event_id = e.id
            ORDER BY "salesRank", e.id
            """)
    List<EventStatsView> findEventStats();
}
