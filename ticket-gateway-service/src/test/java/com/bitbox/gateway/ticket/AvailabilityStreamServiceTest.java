package com.bitbox.gateway.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.bitbox.gateway.client.InventoryClient;
import com.bitbox.gateway.client.dto.InventoryDtos.EventResponse;
import com.bitbox.gateway.error.DownstreamException;
import com.bitbox.gateway.error.GatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Uses virtual time: verifies the 2-second cadence without actually waiting. */
class AvailabilityStreamServiceTest {

    private static final Duration INTERVAL = Duration.ofSeconds(2);

    private final InventoryClient client = mock(InventoryClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void emitsImmediatelyAndThenEveryTwoSeconds() {
        when(client.getEvent(1L)).thenReturn(
                Mono.just(event(10)), Mono.just(event(9)), Mono.just(event(7)));

        StepVerifier.withVirtualTime(() -> service().availability(1L))
                .assertNext(sse -> assertAvailable(sse, 10))
                .expectNoEvent(INTERVAL.minusMillis(1))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(sse -> assertAvailable(sse, 9))
                .expectNoEvent(INTERVAL.minusMillis(1))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(sse -> assertAvailable(sse, 7))
                .thenCancel()
                .verify();

        verify(client, times(3)).getEvent(1L);
    }

    @Test
    void transientFailureEmitsErrorEventAndKeepsStreaming() {
        when(client.getEvent(1L)).thenReturn(
                Mono.just(event(10)),
                Mono.error(GatewayException.inventoryUnavailable(new RuntimeException("down"))),
                Mono.just(event(8)));

        StepVerifier.withVirtualTime(() -> service().availability(1L))
                .assertNext(sse -> assertAvailable(sse, 10))
                .thenAwait(INTERVAL)
                .assertNext(sse -> assertThat(sse.event()).isEqualTo(AvailabilityStreamService.ERROR_EVENT))
                .thenAwait(INTERVAL)
                .assertNext(sse -> assertAvailable(sse, 8))
                .thenCancel()
                .verify();
    }

    @Test
    void unknownEventFailsBeforeStreamingStarts() {
        when(client.getEvent(99L)).thenReturn(
                Mono.error(new DownstreamException(HttpStatus.NOT_FOUND, "{}", null)));

        StepVerifier.withVirtualTime(() -> service().availability(99L))
                .expectError(DownstreamException.class)
                .verify();
    }

    private AvailabilityStreamService service() {
        return new AvailabilityStreamService(client, INTERVAL, clock);
    }

    private static EventResponse event(int available) {
        return new EventResponse(1L, "Concierto", null, 10, available);
    }

    private static void assertAvailable(ServerSentEvent<Object> sse, int expected) {
        assertThat(sse.event()).isEqualTo(AvailabilityStreamService.AVAILABILITY_EVENT);
        assertThat(sse.data()).isInstanceOfSatisfying(AvailabilityUpdate.class,
                update -> assertThat(update.availableTickets()).isEqualTo(expected));
    }
}
