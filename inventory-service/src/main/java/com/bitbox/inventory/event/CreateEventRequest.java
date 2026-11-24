package com.bitbox.inventory.event;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @Schema(example = "Black Friday (IKEA) 2027")
        @NotBlank @Size(max = 255) String name,

        @Schema(example = "2027-03-15T20:00:00")
        @NotNull LocalDateTime eventDate,

        @Schema(description = "Initial inventory; availableTickets starts with this value", example = "100")
        @NotNull @Min(1) Integer totalTickets) {
}
