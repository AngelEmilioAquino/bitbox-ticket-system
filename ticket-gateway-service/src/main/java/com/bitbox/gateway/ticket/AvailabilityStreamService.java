package com.bitbox.gateway.ticket;

import java.time.Clock;
import java.time.Duration;

import com.bitbox.gateway.client.InventoryClient;
import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;
import com.bitbox.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Real-time availability: polls inventory-service every {@code interval} and emits one SSE per tick.
 *
 * <ul>
 *   <li>The first lookup happens before the stream starts, so an unknown event yields a plain HTTP 404
 *       instead of an endless stream of errors.</li>
 *   <li>{@code concatMap} keeps polls sequential: a slow inventory response never causes overlapping calls.</li>
 *   <li>A transient failure becomes an {@code error} SSE and the stream keeps going on the next tick.</li>
 *   <li>When the client disconnects the subscription is cancelled and polling stops.</li>
 * </ul>
 */
@Service
public class AvailabilityStreamService {

    static final String AVAILABILITY_EVENT = "availability";
    static final String ERROR_EVENT = "error";

    private static final Logger log = LoggerFactory.getLogger(AvailabilityStreamService.class);

    private final InventoryClient inventoryClient;
    private final Duration interval;
    private final Clock clock;

    @Autowired
    public AvailabilityStreamService(InventoryClient inventoryClient, GatewayProperties properties) {
        this(inventoryClient, properties.stream().interval(), Clock.systemUTC());
    }

    AvailabilityStreamService(InventoryClient inventoryClient, Duration interval, Clock clock) {
        this.inventoryClient = inventoryClient;
        this.interval = interval;
        this.clock = clock;
    }

    public Flux<ServerSentEvent<Object>> availability(long eventId) {
        return inventoryClient.getEvent(eventId)
                .map(this::toAvailabilityEvent)
                .flatMapMany(first -> Flux.concat(
                        Mono.just(first),
                        Flux.interval(interval)
                                .onBackpressureDrop()
                                .concatMap(tick -> poll(eventId))));
    }

    private Mono<ServerSentEvent<Object>> poll(long eventId) {
        return inventoryClient.getEvent(eventId)
                .map(this::toAvailabilityEvent)
                .onErrorResume(ex -> {
                    log.debug("Availability poll for event {} failed: {}", eventId, ex.getMessage());
                    return Mono.just(ServerSentEvent.builder()
                            .event(ERROR_EVENT)
                            .data((Object) new StreamError("AVAILABILITY_UNAVAILABLE",
                                    "Could not refresh availability, retrying on next tick"))
                            .build());
                });
    }

    private ServerSentEvent<Object> toAvailabilityEvent(
            EventResponse event) {
        return ServerSentEvent.builder()
                .event(AVAILABILITY_EVENT)
                .data((Object) AvailabilityUpdate.from(event, clock.instant()))
                .build();
    }

    public record StreamError(String code, String message) {
    }
}
