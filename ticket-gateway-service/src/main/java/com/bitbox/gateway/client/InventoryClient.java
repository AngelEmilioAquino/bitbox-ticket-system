package com.bitbox.gateway.client;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;
import com.bitbox.gateway.client.dto.InventoryDtos.LoginRequest;
import com.bitbox.gateway.client.dto.InventoryDtos.PurchaseResponse;
import com.bitbox.gateway.client.dto.InventoryDtos.RegisterRequest;
import com.bitbox.gateway.client.dto.InventoryDtos.TokenResponse;
import com.bitbox.gateway.client.dto.InventoryDtos.UserResponse;
import com.bitbox.gateway.config.GatewayProperties;
import com.bitbox.gateway.error.DownstreamException;
import com.bitbox.gateway.error.GatewayException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * The only place that talks to inventory-service. Every call is non-blocking and goes through
 * {@link #handleErrors(Mono)}, which turns technical failures into meaningful gateway errors.
 */
@Component
public class InventoryClient {

    private final WebClient webClient;
    private final Duration timeout;

    @Autowired
    public InventoryClient(WebClient inventoryWebClient, GatewayProperties properties) {
        this(inventoryWebClient, properties.inventory().timeout());
    }

    InventoryClient(WebClient webClient, Duration timeout) {
        this.webClient = webClient;
        this.timeout = timeout;
    }

    public Mono<UserResponse> register(RegisterRequest request) {
        return webClient.post().uri("/api/auth/register")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .transform(this::handleErrors);
    }

    public Mono<TokenResponse> login(LoginRequest request) {
        return webClient.post().uri("/api/auth/login")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .transform(this::handleErrors);
    }

    /** Propagates the caller's bearer token unchanged; inventory-service validates it. */
    public Mono<PurchaseResponse> purchase(long eventId, String authorizationHeader) {
        return webClient.post().uri("/api/events/{id}/purchase", eventId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .bodyToMono(PurchaseResponse.class)
                .transform(this::handleErrors);
    }

    public Mono<EventResponse> getEvent(long eventId) {
        return webClient.get().uri("/api/events/{id}", eventId)
                .retrieve()
                .bodyToMono(EventResponse.class)
                .transform(this::handleErrors);
    }

    private <T> Mono<T> handleErrors(Mono<T> call) {
        return call
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(new DownstreamException(
                        ex.getStatusCode(), ex.getResponseBodyAsString(), ex.getHeaders().getContentType())))
                .onErrorResume(TimeoutException.class, ex -> Mono.error(GatewayException.inventoryTimeout(ex)))
                .onErrorResume(WebClientRequestException.class, ex -> Mono.error(
                        hasCause(ex, TimeoutException.class) || hasCause(ex, io.netty.handler.timeout.TimeoutException.class)
                                ? GatewayException.inventoryTimeout(ex)
                                : GatewayException.inventoryUnavailable(ex)));
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
