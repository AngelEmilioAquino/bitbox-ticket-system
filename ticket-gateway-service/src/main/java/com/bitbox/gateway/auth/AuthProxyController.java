package com.bitbox.gateway.auth;

import com.bitbox.gateway.client.InventoryClient;
import com.bitbox.gateway.client.dto.InventoryDtos.LoginRequest;
import com.bitbox.gateway.client.dto.InventoryDtos.RegisterRequest;
import com.bitbox.gateway.client.dto.InventoryDtos.TokenResponse;
import com.bitbox.gateway.client.dto.InventoryDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Reactive proxy for authentication. Validation and business rules live in inventory-service;
 * its responses (including 400/401/409 errors) are relayed unchanged.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication (proxy)")
public class AuthProxyController {

    private final InventoryClient inventoryClient;

    public AuthProxyController(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a user (forwarded to inventory-service)")
    public Mono<UserResponse> register(@RequestBody RegisterRequest request) {
        return inventoryClient.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and obtain a JWT (forwarded to inventory-service)")
    public Mono<TokenResponse> login(@RequestBody LoginRequest request) {
        return inventoryClient.login(request);
    }
}
