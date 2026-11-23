package com.bitbox.inventory.stats;

import java.util.List;

import com.bitbox.inventory.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Statistics")
public class StatsController {

    private final EventStatsRepository eventStatsRepository;

    public StatsController(EventStatsRepository eventStatsRepository) {
        this.eventStatsRepository = eventStatsRepository;
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    @Operation(summary = "Per-event sales statistics: tickets sold, occupancy %, unique buyers and sales ranking")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public List<EventStatsResponse> stats() {
        return eventStatsRepository.findEventStats().stream().map(EventStatsResponse::from).toList();
    }
}
