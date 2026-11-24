package com.bitbox.gateway.ticket;

import java.time.Clock;
import java.time.Duration;

import com.bitbox.gateway.cache.AvailabilityCache;
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
 * flux is cold and will emit the latest availability for the given eventId to all subscribers.
 * Mono is used to emit the first availability event immediately, then Flux.interval is used to poll for updates.
 * If the poll fails, an error event is emitted with a message and code, but the flux continues to emit availability events on the next tick.
 */
@Service
public class AvailabilityStreamService {

    static final String AVAILABILITY_EVENT = "availability";
    static final String ERROR_EVENT = "error";

    private static final Logger log = LoggerFactory.getLogger(AvailabilityStreamService.class);

    private final InventoryClient inventoryClient;
    private final AvailabilityCache cache;
    private final Duration interval;
    private final Clock clock;

    @Autowired
    public AvailabilityStreamService(InventoryClient inventoryClient, AvailabilityCache cache,
                                     GatewayProperties properties) {
        this(inventoryClient, cache, properties.stream().interval(), Clock.systemUTC());
    }

    AvailabilityStreamService(InventoryClient inventoryClient, AvailabilityCache cache, Duration interval,
                              Clock clock) {
        this.inventoryClient = inventoryClient;
        this.cache = cache;
        this.interval = interval;
        this.clock = clock;
    }

    public Flux<ServerSentEvent<Object>> availability(long eventId) {
        return currentAvailability(eventId)
                .map(this::toAvailabilityEvent)
                .flatMapMany(first -> Flux.concat(
                        Mono.just(first),
                        Flux.interval(interval)
                                .onBackpressureDrop()
                                .concatMap(tick -> poll(eventId))));
    }

    private Mono<ServerSentEvent<Object>> poll(long eventId) {
        return currentAvailability(eventId)
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

    /** Cache-aside: Redis hit use it; miss then store the result. */
    private Mono<EventResponse> currentAvailability(long eventId) {
        return cache.get(eventId)
                .onErrorResume(ex -> {
                    log.debug("Cache read failed for event {}, falling back to inventory: {}", eventId, ex.getMessage());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> inventoryClient.getEvent(eventId)
                        .flatMap(event -> cache.put(event)
                                .onErrorResume(ex -> Mono.just(false))
                                .thenReturn(event))));
    }

    private ServerSentEvent<Object> toAvailabilityEvent(EventResponse event) {
        return ServerSentEvent.builder()
                .event(AVAILABILITY_EVENT)
                .data((Object) AvailabilityUpdate.from(event, clock.instant()))
                .build();
    }

    public record StreamError(String code, String message) {
    }
}
