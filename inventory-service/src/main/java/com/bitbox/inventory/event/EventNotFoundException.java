package com.bitbox.inventory.event;

import com.bitbox.inventory.common.ApiException;
import org.springframework.http.HttpStatus;

public class EventNotFoundException extends ApiException {

    public EventNotFoundException(Long eventId) {
        super(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event " + eventId + " not found");
    }
}
