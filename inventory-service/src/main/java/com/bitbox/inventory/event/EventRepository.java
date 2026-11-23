package com.bitbox.inventory.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Atomic conditional decrement: a single {@code UPDATE ... WHERE available_tickets > 0}.
     *
     * <p>PostgreSQL takes a row lock for the update; concurrent buyers of the same event queue on that lock and,
     * once the previous transaction commits, re-evaluate the {@code WHERE} clause against the committed row.
     * When the stock reaches zero the condition stops matching, so overselling is impossible and no
     * application-level retry is needed. {@code version} is incremented too, so the {@code @Version}
     * optimistic lock still protects any other JPA-based modification of the event.</p>
     *
     * @return 1 if a ticket was taken, 0 if the event is sold out or does not exist
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Event e
               SET e.availableTickets = e.availableTickets - 1,
                   e.version = e.version + 1
             WHERE e.id = :id
               AND e.availableTickets > 0
            """)
    int decrementAvailableTickets(@Param("id") Long id);

    @Query("SELECT e.availableTickets FROM Event e WHERE e.id = :id")
    Integer findAvailableTicketsById(@Param("id") Long id);
}
