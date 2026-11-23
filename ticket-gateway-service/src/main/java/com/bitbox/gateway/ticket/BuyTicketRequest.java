package com.bitbox.gateway.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BuyTicketRequest(
        @Schema(description = "Event to buy one ticket for", example = "1")
        @NotNull @Positive Long eventId) {
}
