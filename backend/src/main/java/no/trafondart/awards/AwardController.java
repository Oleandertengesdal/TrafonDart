package no.trafondart.awards;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AwardController {

    private final AwardService service;
    private final YearAwardService years;

    public AwardController(AwardService service, YearAwardService years) {
        this.service = service;
        this.years = years;
    }

    /** This year's race for Player of the Year, and last year's winner. */
    @GetMapping("/api/awards/year/current")
    public YearAwardService.YearRace currentYear() {
        return years.race();
    }

    /** Every decided year, newest first. */
    @GetMapping("/api/awards/years")
    public List<YearAwardService.YearResult> yearHallOfFame() {
        return years.hallOfFame();
    }

    /** This month's live race for Player of the Month, the category leaders and last month's winner. */
    @GetMapping("/api/awards/current")
    public AwardService.Race current() {
        return service.race();
    }

    /** Every decided month, newest first. */
    @GetMapping("/api/awards")
    public List<AwardService.MonthResult> hallOfFame(@RequestParam(defaultValue = "24") int limit) {
        return service.hallOfFame(limit);
    }

    /** The badges a player has won. */
    @GetMapping("/api/users/{username}/awards")
    public List<AwardService.AwardView> badges(@PathVariable String username) {
        years.ensureFinalized();
        return service.badges(username);
    }

    /** Re-decide every past month (e.g. after ratings were recalculated). */
    @PostMapping("/api/admin/awards/recompute")
    public Map<String, Integer> recompute() {
        int months = service.recomputeAll();
        return Map.of("months", months, "years", years.ensureFinalized());
    }
}
