package no.trafondart.analysis;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    /** Turn-by-turn legs, score bands, heatmaps and pre-match win probability. */
    @GetMapping("/api/matches/{id}/breakdown")
    public AnalyticsService.MatchBreakdown breakdown(@PathVariable UUID id) {
        return service.breakdown(id);
    }

    /** Heatmap, treble/double rates, hold/break, best leg, favourite finishes … across all completed matches. */
    @GetMapping("/api/users/{username}/dart-stats")
    public AnalyticsService.DartStats dartStats(@PathVariable String username) {
        return service.dartStats(username);
    }
}
