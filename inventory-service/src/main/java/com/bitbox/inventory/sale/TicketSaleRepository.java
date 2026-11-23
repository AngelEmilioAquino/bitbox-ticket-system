package com.bitbox.inventory.sale;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {

    long countByEventId(Long eventId);
}
