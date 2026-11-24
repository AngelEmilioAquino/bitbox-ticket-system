package com.bitbox.gateway.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("gateway")
public record GatewayProperties(@Valid @NotNull Inventory inventory, @Valid @NotNull Stream stream,
                                @Valid @NotNull Cache cache) {

    public record Inventory(@NotBlank String baseUrl, @NotNull Duration connectTimeout, @NotNull Duration timeout) {
    }

    public record Stream(@NotNull Duration interval) {
    }

    public record Cache(@NotNull Duration ttl) {
    }
}
