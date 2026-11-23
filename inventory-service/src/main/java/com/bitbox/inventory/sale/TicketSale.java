package com.bitbox.inventory.sale;

import java.time.LocalDateTime;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_sales")
public class TicketSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected TicketSale() {
        // required by JPA
    }

    public TicketSale(Event event, User user, LocalDateTime purchaseDate) {
        this.event = event;
        this.user = user;
        this.purchaseDate = purchaseDate;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public Event getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }
}
