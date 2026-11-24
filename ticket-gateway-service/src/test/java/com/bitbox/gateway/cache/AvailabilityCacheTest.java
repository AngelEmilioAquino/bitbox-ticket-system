package com.bitbox.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/** Against a real Redis (Testcontainers): JSON round trip, TTL and eviction. */
class AvailabilityCacheTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;

    private ReactiveStringRedisTemplate redis;
    private AvailabilityCache cache;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
        cache = new AvailabilityCache(redis, JsonMapper.builder().build(), Duration.ofSeconds(30));
    }

    @Test
    void missOnEmptyCache() {
        StepVerifier.create(cache.get(1L)).verifyComplete();
    }

    @Test
    void putThenGetReturnsTheSameEvent() {
        EventResponse event = new EventResponse(1L, "Concierto", LocalDateTime.of(2027, 3, 15, 20, 0), 100, 42);

        StepVerifier.create(cache.put(event).then(cache.get(1L)))
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    void entriesExpireWithTheConfiguredTtl() {
        cache.put(new EventResponse(1L, "Concierto", null, 10, 5)).block();

        Duration ttl = redis.getExpire(AvailabilityCache.key(1L)).block();
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void evictRemovesTheEntry() {
        cache.put(new EventResponse(1L, "Concierto", null, 10, 5)).block();

        StepVerifier.create(cache.evict(1L)).expectNext(true).verifyComplete();
        StepVerifier.create(cache.get(1L)).verifyComplete();
    }
}