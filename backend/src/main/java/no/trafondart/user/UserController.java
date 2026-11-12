package no.trafondart.user;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.stats.StatsService;
import no.trafondart.stats.StatsViews;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final StatsService stats;

    public UserController(UserRepository users, StatsService stats) {
        this.users = users;
        this.stats = stats;
    }

    /** Active members, for opponent and participant pickers. */
    @GetMapping
    public List<UserDtos.PlayerSummary> list() {
        return users.findAllByEnabledTrueOrderByUsernameAsc().stream().map(UserDtos.PlayerSummary::of).toList();
    }

    @GetMapping("/leaderboard")
    public List<StatsViews.LeaderboardRow> leaderboard() {
        return stats.leaderboard();
    }

    @PatchMapping("/me")
    @Transactional
    public UserDtos.Me updateMe(@Valid @RequestBody UserDtos.UpdateProfileRequest req) {
        User user = users.findById(AuthUser.current().id()).orElseThrow(ApiException::notFound);
        if (req.realName() != null) user.setRealName(req.realName());
        if (req.preferredLocale() != null) user.setPreferredLocale(req.preferredLocale());
        return UserDtos.Me.of(user);
    }

    @GetMapping("/{username}/stats")
    public StatsViews.ProfileStats profile(@PathVariable String username) {
        return stats.profile(username);
    }

    @GetMapping("/{username}/head-to-head")
    public List<StatsViews.HeadToHeadRow> headToHead(@PathVariable String username) {
        return stats.headToHead(username);
    }

    @GetMapping("/{username}/head-to-head/{other}")
    public StatsViews.HeadToHeadRow headToHeadWith(@PathVariable String username, @PathVariable String other) {
        return stats.headToHead(username, other);
    }
}
