package com.bitbox.gateway.cache;

import java.time.Duration;

import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;
import com.bitbox.gateway.config.GatewayProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reactive Redis cache of each event's availability, shared by every SSE subscriber (and every gateway
 * instance). Without it, N clients watching the same event cause N/2 requests per second to inventory;
 * with it, inventory is queried at most once per TTL per event, or right after a purchase invalidates it.
 */
@Component
public class AvailabilityCache {

    static final String KEY_PREFIX = "availability:event:";

    private final ReactiveStringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    @Autowired
    public AvailabilityCache(ReactiveStringRedisTemplate redis, JsonMapper jsonMapper, GatewayProperties properties) {
        this(redis, jsonMapper, properties.cache().ttl());
    }

    AvailabilityCache(ReactiveStringRedisTemplate redis, JsonMapper jsonMapper, Duration ttl) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = ttl;
    }

    /** Cached value, or an empty Mono on a cache miss. */
    public Mono<EventResponse> get(long eventId) {
        return redis.opsForValue().get(key(eventId))
                .map(json -> jsonMapper.readValue(json, EventResponse.class));
    }

    public Mono<Boolean> put(EventResponse event) {
        return Mono.fromCallable(() -> jsonMapper.writeValueAsString(event))
                .flatMap(json -> redis.opsForValue().set(key(event.id()), json, ttl));
    }

    /** Removes the entry so the next read goes to inventory-service. Emits true if something was deleted. */
    public Mono<Boolean> evict(long eventId) {
        return redis.delete(key(eventId)).map(deleted -> deleted > 0);
    }

    static String key(long eventId) {
        return KEY_PREFIX + eventId;
    }
}