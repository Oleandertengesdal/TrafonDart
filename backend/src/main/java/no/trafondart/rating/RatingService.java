package no.trafondart.rating;

import no.trafondart.match.Match;
import no.trafondart.match.MatchRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies Glicko-2 to completed, rated matches.
 *
 * <p>Each rated match is its own rating period for the two players. Before the update, each player's RD is grown
 * for every week they went without a rated match, so returning players move faster.
 */
@Service
public class RatingService {

    /** Length of one "idle" rating period used for RD inflation. */
    public static final Duration IDLE_PERIOD = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final UserRepository users;
    private final MatchRepository matches;
    private final RatingHistoryRepository history;

    public RatingService(UserRepository users, MatchRepository matches, RatingHistoryRepository history) {
        this.users = users;
        this.matches = matches;
        this.history = history;
    }

    @Transactional
    public void applyMatch(Match match) {
        // Lock both players (in id order, so concurrent completions cannot deadlock).
        Map<Long, User> locked = users.findAllByIdForUpdate(List.of(match.getPlayer1Id(), match.getPlayer2Id()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        User p1 = locked.get(match.getPlayer1Id());
        User p2 = locked.get(match.getPlayer2Id());
        apply(match, p1, p2, match.getCompletedAt());
    }

    private void apply(Match match, User p1, User p2, Instant at) {
        Glicko2.Rating r1 = Glicko2.inflateForInactivity(p1.glicko(), idlePeriods(p1.getLastRatedAt(), at));
        Glicko2.Rating r2 = Glicko2.inflateForInactivity(p2.glicko(), idlePeriods(p2.getLastRatedAt(), at));
        double s1 = match.getWinnerId().equals(p1.getId()) ? 1.0 : 0.0;

        Glicko2.Rating n1 = Glicko2.update(r1, List.of(new Glicko2.Result(r2, s1)));
        Glicko2.Rating n2 = Glicko2.update(r2, List.of(new Glicko2.Result(r1, 1.0 - s1)));

        history.save(new RatingHistory(p1.getId(), match.getId(), r1, n1, at));
        history.save(new RatingHistory(p2.getId(), match.getId(), r2, n2, at));
        p1.applyRating(n1, at);
        p2.applyRating(n2, at);
    }

    /**
     * Rebuilds every rating from scratch by replaying all completed rated matches in order. Used after an admin
     * voids a match. At friend-group scale (hundreds of matches) this takes milliseconds.
     */
    @Transactional
    public int recomputeAll() {
        history.deleteEverything();
        Map<Long, User> byId = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        byId.values().forEach(User::resetRating);
        List<Match> rated = matches.findRatedCompletedInOrder();
        for (Match m : rated) {
            apply(m, byId.get(m.getPlayer1Id()), byId.get(m.getPlayer2Id()), m.getCompletedAt());
        }
        log.info("Recomputed ratings from {} matches", rated.size());
        return rated.size();
    }

    static double idlePeriods(Instant lastRated, Instant now) {
        if (lastRated == null || now == null || !now.isAfter(lastRated)) return 0;
        return Math.floor(Duration.between(lastRated, now).toMillis() / (double) IDLE_PERIOD.toMillis());
    }
}
