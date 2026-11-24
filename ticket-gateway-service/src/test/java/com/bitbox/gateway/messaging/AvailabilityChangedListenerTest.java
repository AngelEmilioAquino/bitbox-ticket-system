package com.bitbox.gateway.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.bitbox.gateway.cache.AvailabilityCache;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AvailabilityChangedListenerTest {

    private final AvailabilityCache cache = mock(AvailabilityCache.class);
    private final AvailabilityChangedListener listener = new AvailabilityChangedListener(cache);

    @Test
    void availabilityChangeInvalidatesTheCachedEvent() {
        when(cache.evict(7L)).thenReturn(Mono.just(true));

        listener.onAvailabilityChanged(new AvailabilityChangedMessage(7L, 3, Instant.now()));

        verify(cache).evict(7L);
    }

    /** Throwing would make RabbitMQ redeliver the message in a tight loop while Redis is down. */
    @Test
    void redisFailureIsSwallowedNotRethrown() {
        when(cache.evict(7L)).thenReturn(Mono.error(new IllegalStateException("Redis down")));

        assertThatCode(() -> listener.onAvailabilityChanged(new AvailabilityChangedMessage(7L, 3, Instant.now())))
                .doesNotThrowAnyException();
    }
}