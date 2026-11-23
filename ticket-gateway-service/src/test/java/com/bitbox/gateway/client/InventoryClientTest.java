package com.bitbox.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.bitbox.gateway.client.dto.InventoryDtos.LoginRequest;
import com.bitbox.gateway.error.DownstreamException;
import com.bitbox.gateway.error.GatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Exercises the real WebClient pipeline against a stubbed {@link ExchangeFunction}: no network,
 * but request building, decoding and the reactive error handling are the production code.
 */
class InventoryClientTest {

    /** Large enough to absorb cold-start latency on busy CI machines; only slowInventoryBecomes504 tests timeouts. */
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();

    @Test
    void purchasePropagatesBearerTokenToInventory() {
        InventoryClient client = clientReturning(json(HttpStatus.CREATED, """
                {"saleId": 10, "eventId": 3, "userId": 7, "purchaseDate": "2027-01-01T10:00:00", "availableTickets": 4}
                """));

        StepVerifier.create(client.purchase(3L, "Bearer abc.def.ghi"))
                .assertNext(response -> {
                    assertThat(response.saleId()).isEqualTo(10L);
                    assertThat(response.availableTickets()).isEqualTo(4);
                })
                .verifyComplete();

        ClientRequest sent = lastRequest.get();
        assertThat(sent.method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.url().getPath()).isEqualTo("/api/events/3/purchase");
        assertThat(sent.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer abc.def.ghi");
    }

    @Test
    void downstreamErrorKeepsOriginalStatusAndBody() {
        String problem = """
                {"status": 409, "code": "SOLD_OUT", "detail": "No tickets available for event 3"}
                """;
        InventoryClient client = clientReturning(ClientResponse.create(HttpStatus.CONFLICT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(problem)
                .build());

        StepVerifier.create(client.purchase(3L, "Bearer t"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(DownstreamException.class);
                    DownstreamException downstream = (DownstreamException) error;
                    assertThat(downstream.getStatus().value()).isEqualTo(409);
                    assertThat(downstream.getBody()).contains("SOLD_OUT");
                    assertThat(downstream.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
                })
                .verify();
    }

    @Test
    void unreachableInventoryBecomes503() {
        // Same exception the Reactor Netty connector raises when the TCP connection is refused
        InventoryClient client = new InventoryClient(webClient(request -> Mono.error(new WebClientRequestException(
                new ConnectException("refused"), request.method(), request.url(), request.headers()))),
                GENEROUS_TIMEOUT);

        StepVerifier.create(client.login(new LoginRequest("a@b.com", "x")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayException.class);
                    assertThat(((GatewayException) error).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                })
                .verify();
    }

    @Test
    void slowInventoryBecomes504() {
        InventoryClient client = new InventoryClient(webClient(request -> Mono.never()), Duration.ofMillis(100));

        StepVerifier.create(client.getEvent(1L))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayException.class);
                    assertThat(((GatewayException) error).getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                })
                .verify(Duration.ofSeconds(5));
    }

    private InventoryClient clientReturning(ClientResponse response) {
        return new InventoryClient(webClient(request -> Mono.just(response)), GENEROUS_TIMEOUT);
    }

    private WebClient webClient(ExchangeFunction exchange) {
        return WebClient.builder()
                .baseUrl("http://inventory-service")
                .exchangeFunction(request -> {
                    lastRequest.set(request);
                    return exchange.exchange(request);
                })
                .build();
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
