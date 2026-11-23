package com.bitbox.inventory.stats;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    private final EventStatsRepository eventStatsRepository;

    public StatsService(EventStatsRepository eventStatsRepository) {
        this.eventStatsRepository = eventStatsRepository;
    }

    @Transactional(readOnly = true)
    public List<EventStatsResponse> getEventStats() {
        return eventStatsRepository.findEventStats().stream()
                .map(EventStatsResponse::from)
                .toList();
    }
}
