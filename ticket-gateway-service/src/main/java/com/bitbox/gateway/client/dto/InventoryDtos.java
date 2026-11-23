package com.bitbox.gateway.client.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * HTTP contract with inventory-service. Deliberately duplicated here (no shared module) so the two
 * services can be built and deployed independently; the contract is the JSON, not a Java artifact.
 */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record RegisterRequest(
            @Schema(example = "ana@bitbox.com") String email,
            @Schema(example = "S3cret-pass") String password) {
    }

    public record LoginRequest(
            @Schema(example = "ana@bitbox.com") String email,
            @Schema(example = "S3cret-pass") String password) {
    }

    public record UserResponse(Long id, String email) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record EventResponse(Long id, String name, LocalDateTime eventDate,
                                Integer totalTickets, Integer availableTickets) {
    }

    public record PurchaseResponse(Long saleId, Long eventId, Long userId,
                                   LocalDateTime purchaseDate, Integer availableTickets) {
    }
}
