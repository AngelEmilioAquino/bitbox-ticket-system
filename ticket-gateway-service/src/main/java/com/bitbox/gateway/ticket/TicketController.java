package com.bitbox.gateway.ticket;

import com.bitbox.gateway.client.InventoryClient;
import com.bitbox.gateway.client.dto.InventoryDtos.PurchaseResponse;
import com.bitbox.gateway.config.OpenApiConfig;
import com.bitbox.gateway.error.GatewayException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets")
public class TicketController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final InventoryClient inventoryClient;
    private final AvailabilityStreamService availabilityStreamService;

    public TicketController(InventoryClient inventoryClient, AvailabilityStreamService availabilityStreamService) {
        this.inventoryClient = inventoryClient;
        this.availabilityStreamService = availabilityStreamService;
    }

    /**
     * The gateway only checks that a bearer token is present (fail fast, no useless upstream call) and
     * propagates it unchanged. Cryptographic validation happens in inventory-service, the only holder of
     * the signing secret, so it is not duplicated here.
     */
    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy one ticket (bearer token propagated to inventory-service)")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "Ticket purchased")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "Event not found")
    @ApiResponse(responseCode = "409", description = "SOLD_OUT or PURCHASE_CONFLICT")
    @ApiResponse(responseCode = "503", description = "inventory-service unreachable")
    @ApiResponse(responseCode = "504", description = "inventory-service timed out")
    public Mono<PurchaseResponse> buy(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            @Valid @RequestBody Mono<BuyTicketRequest> request) {
        if (!hasBearerToken(authorization)) {
            return Mono.error(GatewayException.missingBearerToken());
        }
        return request.flatMap(body -> inventoryClient.purchase(body.eventId(), authorization));
    }

    @GetMapping(path = "/stream/{eventId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time availability of an event (Server-Sent Events, one update every 2 seconds)")
    public Flux<ServerSentEvent<Object>> stream(@PathVariable long eventId) {
        return availabilityStreamService.availability(eventId);
    }

    private static boolean hasBearerToken(String authorization) {
        return authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && !authorization.substring(BEARER_PREFIX.length()).isBlank();
    }
}
