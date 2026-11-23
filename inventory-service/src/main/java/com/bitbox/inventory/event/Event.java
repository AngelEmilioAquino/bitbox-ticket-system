package com.bitbox.inventory.event;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "total_tickets", nullable = false)
    private Integer totalTickets;

    @Column(name = "available_tickets", nullable = false)
    private Integer availableTickets;

    /**
     * Optimistic locking: Hibernate issues {@code UPDATE ... WHERE id = ? AND version = ?}.
     * If another transaction changed the row first, 0 rows match and the update fails,
     * so two buyers can never both take the same last ticket.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Event() {
        // required by JPA
    }

    public Event(String name, LocalDateTime eventDate, int totalTickets) {
        if (totalTickets <= 0) {
            throw new IllegalArgumentException("totalTickets must be greater than zero");
        }
        this.name = name;
        this.eventDate = eventDate;
        this.totalTickets = totalTickets;
        this.availableTickets = totalTickets;
    }

    /**
     * Takes one ticket from the stock.
     *
     * @return {@code false} if the event is sold out (the stock is left untouched)
     */
    public boolean tryReserveTicket() {
        if (availableTickets <= 0) {
            return false;
        }
        availableTickets--;
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public Integer getTotalTickets() {
        return totalTickets;
    }

    public Integer getAvailableTickets() {
        return availableTickets;
    }

    public Long getVersion() {
        return version;
    }
}
