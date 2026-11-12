package no.trafondart.history;

import no.trafondart.awards.YearAwardService;
import no.trafondart.common.ApiException;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lifetime achievements and the year recap for any player (both are visible to every logged-in member). */
@RestController
@RequestMapping("/api/users/{username}")
public class HistoryController {

    private final UserRepository users;
    private final AchievementService achievements;
    private final RecapService recaps;
    private final YearAwardService years;

    public HistoryController(UserRepository users, AchievementService achievements, RecapService recaps, YearAwardService years) {
        this.users = users;
        this.achievements = achievements;
        this.recaps = recaps;
        this.years = years;
    }

    @GetMapping("/achievements")
    public AchievementService.Cabinet achievements(@PathVariable String username) {
        return achievements.cabinet(find(username));
    }

    @GetMapping("/recap")
    public RecapService.Recap recap(@PathVariable String username, @RequestParam(required = false) Integer year) {
        User user = find(username);
        return recaps.recap(user, years.checkYear(year));
    }

    private User find(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
    }
}
