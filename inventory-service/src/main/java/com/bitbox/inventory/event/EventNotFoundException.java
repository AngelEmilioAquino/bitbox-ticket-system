package com.bitbox.inventory.event;

import com.bitbox.inventory.common.BusinessException;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException(Long eventId) {
        super("EVENT_NOT_FOUND", "Event " + eventId + " not found");
    }
}
