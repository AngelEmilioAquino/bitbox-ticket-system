package com.bitbox.inventory.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class EventTest {

    @Test
    void newEventStartsWithFullAvailability() {
        Event event = new Event("Concierto", LocalDateTime.now(), 3);

        assertThat(event.getAvailableTickets()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveInventory() {
        assertThatThrownBy(() -> new Event("Concierto", LocalDateTime.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
