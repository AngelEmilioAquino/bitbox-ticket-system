package com.bitbox.inventory.sale;

import java.time.LocalDateTime;

public record PurchaseResponse( Long saleId, Long eventId, Long userId, LocalDateTime purchaseDate, Integer availableTickets) {
}
