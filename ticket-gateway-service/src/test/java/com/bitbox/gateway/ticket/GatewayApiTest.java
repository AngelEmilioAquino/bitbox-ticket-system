package com.bitbox.gateway.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import com.bitbox.gateway.client.InventoryClient;
import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;
import com.bitbox.gateway.client.dto.InventoryDtos.PurchaseResponse;
import com.bitbox.gateway.client.dto.InventoryDtos.RegisterRequest;
import com.bitbox.gateway.client.dto.InventoryDtos.UserResponse;
import com.bitbox.gateway.error.DownstreamException;
import com.bitbox.gateway.error.GatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Full WebFlux stack (routing, validation, error handling, SSE) with inventory-service mocked out. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private InventoryClient inventoryClient;

    @Test
    void registerIsProxied() {
        when(inventoryClient.register(any(RegisterRequest.class)))
                .thenReturn(Mono.just(new UserResponse(1L, "ana@bitbox.com")));

        webTestClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email": "ana@bitbox.com", "password": "S3cret-pass"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.email").isEqualTo("ana@bitbox.com");
    }

    @Test
    void loginErrorFromInventoryIsRelayedUnchanged() {
        when(inventoryClient.login(any())).thenReturn(Mono.error(new DownstreamException(HttpStatus.UNAUTHORIZED,
                "{\"status\":401,\"code\":\"INVALID_CREDENTIALS\"}", MediaType.APPLICATION_PROBLEM_JSON)));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\": \"ana@bitbox.com\", \"password\": \"bad\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void buyWithoutTokenIsRejectedWithoutCallingInventory() {
        webTestClient.post().uri("/api/tickets/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\": 1}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("MISSING_BEARER_TOKEN");

        verify(inventoryClient, never()).purchase(anyLong(), anyString());
    }

    @Test
    void buyPropagatesTokenAndReturnsPurchase() {
        when(inventoryClient.purchase(1L, "Bearer good.token"))
                .thenReturn(Mono.just(new PurchaseResponse(5L, 1L, 7L, LocalDateTime.of(2027, 1, 1, 10, 0), 9)));

        webTestClient.post().uri("/api/tickets/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good.token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\": 1}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.saleId").isEqualTo(5)
                .jsonPath("$.availableTickets").isEqualTo(9);
    }

    @Test
    void buySoldOutRelays409() {
        when(inventoryClient.purchase(1L, "Bearer t")).thenReturn(Mono.error(new DownstreamException(
                HttpStatus.CONFLICT, "{\"status\":409,\"code\":\"SOLD_OUT\"}", MediaType.APPLICATION_PROBLEM_JSON)));

        webTestClient.post().uri("/api/tickets/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer t")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\": 1}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.code").isEqualTo("SOLD_OUT");
    }

    @Test
    void buyWhenInventoryIsDownReturns503() {
        when(inventoryClient.purchase(1L, "Bearer t"))
                .thenReturn(Mono.error(GatewayException.inventoryUnavailable(new RuntimeException("refused"))));

        webTestClient.post().uri("/api/tickets/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer t")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\": 1}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody().jsonPath("$.code").isEqualTo("INVENTORY_UNAVAILABLE");
    }

    @Test
    void buyValidatesBody() {
        webTestClient.post().uri("/api/tickets/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer t")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void streamIsPublicAndUsesServerSentEvents() {
        when(inventoryClient.getEvent(1L))
                .thenReturn(Mono.just(new EventResponse(1L, "Concierto", null, 10, 6)));

        var body = webTestClient.get().uri("/api/tickets/stream/1")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody();

        StepVerifier.create(body.take(1))
                .expectNextMatches(data -> data.contains("\"availableTickets\":6"))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void streamOfUnknownEventReturns404() {
        when(inventoryClient.getEvent(99L)).thenReturn(Mono.error(new DownstreamException(HttpStatus.NOT_FOUND,
                "{\"status\":404,\"code\":\"EVENT_NOT_FOUND\"}", MediaType.APPLICATION_PROBLEM_JSON)));

        webTestClient.get().uri("/api/tickets/stream/99")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void openApiDocumentIsPublished() {
        webTestClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer")
                .jsonPath("$.paths['/api/tickets/buy']").exists()
                .jsonPath("$.paths['/api/tickets/stream/{eventId}']").exists();
    }
}
