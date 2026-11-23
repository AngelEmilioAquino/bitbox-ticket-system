package com.bitbox.inventory.sale;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry budget for optimistic-locking conflicts during a purchase.
 * Backoff is exponential with full jitter, capped at {@code maxBackoff}.
 */
@Validated
@ConfigurationProperties("purchase.retry")
public record PurchaseRetryProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff) {
}
