package com.bitbox.inventory.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = new Event(request.name().trim(), request.eventDate(), request.totalTickets());
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getById(Long id) {
        return eventRepository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}
