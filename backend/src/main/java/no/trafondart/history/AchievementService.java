package no.trafondart.history;

import no.trafondart.awards.AwardEntities;
import no.trafondart.awards.AwardService;
import no.trafondart.awards.MonthlyAwardRepository;
import no.trafondart.awards.YearEntities;
import no.trafondart.awards.YearlyAwardRepository;
import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;
import no.trafondart.match.MatchRecords.LegRecord;
import no.trafondart.party.GameType;
import no.trafondart.tournament.Tournament;
import no.trafondart.tournament.TournamentRepository;
import no.trafondart.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lifetime achievements for the trophy cabinet (see docs/ACHIEVEMENTS.md).
 *
 * <p>Nothing is stored: every achievement is worked out from the darts, matches, games and awards that exist right
 * now, so a voided match takes its achievements with it. Only matches that are rated, or that one of the two
 * players set up, count – nobody can unlock things for someone else by inventing casual matches.
 */
@Service
public class AchievementService {

    /** Every achievement, in the order the cabinet shows them. Count-based ones have a target. */
    public enum Code {
        FIRST_WIN(0), MATCHES_10(10), MATCHES_50(50), MATCHES_100(100), WINS_25(25), STREAK_5(5),
        FIRST_180(0), MAX_COLLECTOR(10), TON_PLUS_CHECKOUT(0), BIG_FISH(0), BULL_FINISH(0), SHORT_LEG(0), NINE_DARTER(0),
        AVERAGE_50(0), AVERAGE_70(0), GIANT_KILLER(0), TOURNAMENT_WIN(0), PLAYER_OF_MONTH(0), PLAYER_OF_YEAR(0),
        CRICKET_WIN(0), KILLER_WIN(0), CLOCK_UNDER_40(0), DARTS_10K(10_000);

        public final int target;

        Code(int target) {
            this.target = target;
        }
    }

    public static final int GIANT_GAP = 150;

    /** One cabinet entry. {@code progress}/{@code target} are null for one-off achievements. */
    public record Achievement(String code, boolean unlocked, Instant unlockedAt, UUID matchId, UUID gameId, Integer progress,
                              Integer target) {
    }

    public record Cabinet(int unlocked, int total, List<Achievement> items) {
    }

    private final PlayerHistoryService history;
    private final AwardService awards;
    private final MonthlyAwardRepository monthly;
    private final YearlyAwardRepository yearly;
    private final TournamentRepository tournaments;

    public AchievementService(PlayerHistoryService history, AwardService awards, MonthlyAwardRepository monthly,
                              YearlyAwardRepository yearly, TournamentRepository tournaments) {
        this.history = history;
        this.awards = awards;
        this.monthly = monthly;
        this.yearly = yearly;
        this.tournaments = tournaments;
    }

    private static final class Unlock {
        Instant at;
        UUID matchId;
        UUID gameId;
        int progress;
    }

    @Transactional(readOnly = true)
    public Cabinet cabinet(User user) {
        PlayerHistory h = history.load(user, Instant.EPOCH, Instant.now().plusSeconds(60), true);
        Map<Code, Unlock> got = new LinkedHashMap<>();
        for (Code c : Code.values()) got.put(c, new Unlock());

        int played = 0, wins = 0, streak = 0, total180 = 0;
        long darts = 0;
        // matches and games in the order they happened, so counters unlock at the right moment
        record Event(Instant at, PlayerHistory.MatchEntry match, PlayerHistory.GameEntry game) {
        }
        List<Event> events = new ArrayList<>();
        h.matches().forEach(m -> events.add(new Event(m.at(), m, null)));
        h.games().forEach(g -> events.add(new Event(g.at(), null, g)));
        events.sort(Comparator.comparing(Event::at));

        for (Event e : events) {
            if (e.game() != null) {
                PlayerHistory.GameEntry g = e.game();
                darts += g.darts();
                if (darts >= Code.DARTS_10K.target) once(got, Code.DARTS_10K, g.at(), null, g.gameId());
                if (g.won() && g.type() == GameType.CRICKET) once(got, Code.CRICKET_WIN, g.at(), null, g.gameId());
                if (g.won() && g.type() == GameType.KILLER) once(got, Code.KILLER_WIN, g.at(), null, g.gameId());
                if (g.won() && g.type() == GameType.AROUND_THE_CLOCK && g.darts() < 40) once(got, Code.CLOCK_UNDER_40, g.at(), null, g.gameId());
                continue;
            }
            PlayerHistory.MatchEntry m = e.match();
            UUID id = m.match().getId();
            Instant at = m.at();
            played++;
            if (m.won()) {
                wins++;
                streak++;
            } else {
                streak = 0;
            }
            if (m.won()) once(got, Code.FIRST_WIN, at, id, null);
            if (played >= 10) once(got, Code.MATCHES_10, at, id, null);
            if (played >= 50) once(got, Code.MATCHES_50, at, id, null);
            if (played >= 100) once(got, Code.MATCHES_100, at, id, null);
            if (wins >= 25) once(got, Code.WINS_25, at, id, null);
            if (streak >= 5) once(got, Code.STREAK_5, at, id, null);
            if (m.mine() != null) {
                darts += m.mine().getDartsThrown();
                total180 += m.mine().getCount180();
                if (m.mine().getCount180() > 0) once(got, Code.FIRST_180, at, id, null);
                if (total180 >= 10) once(got, Code.MAX_COLLECTOR, at, id, null);
                if (m.mine().getHighestCheckout() >= 100) once(got, Code.TON_PLUS_CHECKOUT, at, id, null);
                if (m.mine().getHighestCheckout() == 170) once(got, Code.BIG_FISH, at, id, null);
                if (m.match().getStartScore() >= 301 && m.average() >= 50) once(got, Code.AVERAGE_50, at, id, null);
                if (m.match().getStartScore() >= 301 && m.average() >= 70) once(got, Code.AVERAGE_70, at, id, null);
            }
            if (darts >= Code.DARTS_10K.target) once(got, Code.DARTS_10K, at, id, null);
            if (m.match().getStartScore() == 501) {
                for (LegRecord leg : m.legs()) {
                    if (!user.getId().equals(leg.getWinnerId())) continue;
                    if (leg.getWinnerDarts() <= 15) once(got, Code.SHORT_LEG, at, id, null);
                    if (leg.getWinnerDarts() == 9) once(got, Code.NINE_DARTER, at, id, null);
                }
            }
            if (bullFinish(m)) once(got, Code.BULL_FINISH, at, id, null);
            if (m.won() && m.match().isRated() && m.myRatingBefore() != null && m.opponentRatingBefore() != null
                    && m.opponentRatingBefore() - m.myRatingBefore() >= GIANT_GAP) {
                once(got, Code.GIANT_KILLER, at, id, null);
            }
        }
        got.get(Code.MATCHES_10).progress = played;
        got.get(Code.MATCHES_50).progress = played;
        got.get(Code.MATCHES_100).progress = played;
        got.get(Code.WINS_25).progress = wins;
        got.get(Code.MAX_COLLECTOR).progress = total180;
        got.get(Code.DARTS_10K).progress = (int) Math.min(darts, Integer.MAX_VALUE);
        got.get(Code.STREAK_5).progress = longestStreak(h);

        awards.ensureFinalized();
        monthly.findByUser(user.getId()).stream()
                .filter(a -> a.getAward() == AwardEntities.Award.PLAYER_OF_MONTH)
                .map(a -> YearMonth.parse(a.getPeriod()))
                .min(Comparator.naturalOrder())
                .ifPresent(first -> once(got, Code.PLAYER_OF_MONTH, awards.start(first.plusMonths(1)), null, null));
        yearly.findByUser(user.getId()).stream()
                .map(YearEntities.YearlyAward::getYear)
                .min(Comparator.naturalOrder())
                .ifPresent(y -> once(got, Code.PLAYER_OF_YEAR, awards.start(YearMonth.of(y + 1, 1)), null, null));
        tournaments.findAllByWinnerId(user.getId()).stream()
                .filter(t -> t.getStatus() == Tournament.Status.COMPLETED && t.getCompletedAt() != null)
                .map(Tournament::getCompletedAt)
                .min(Comparator.naturalOrder())
                .ifPresent(at -> once(got, Code.TOURNAMENT_WIN, at, null, null));

        List<Achievement> items = new ArrayList<>();
        int unlocked = 0;
        for (Map.Entry<Code, Unlock> e : got.entrySet()) {
            Code c = e.getKey();
            Unlock u = e.getValue();
            boolean done = u.at != null;
            if (done) unlocked++;
            Integer target = c.target > 0 ? c.target : null;
            Integer progress = target == null ? null : Math.min(u.progress, target);
            items.add(new Achievement(c.name(), done, u.at, u.matchId, u.gameId, progress, target));
        }
        return new Cabinet(unlocked, items.size(), items);
    }

    private static void once(Map<Code, Unlock> got, Code code, Instant at, UUID matchId, UUID gameId) {
        Unlock u = got.get(code);
        if (u.at != null) return;
        u.at = at;
        u.matchId = matchId;
        u.gameId = gameId;
    }

    private static int longestStreak(PlayerHistory h) {
        int best = 0, run = 0;
        for (PlayerHistory.MatchEntry m : h.matches()) {
            run = m.won() ? run + 1 : 0;
            best = Math.max(best, run);
        }
        return best;
    }

    /** Did the player win a leg in this match with the inner bull? */
    static boolean bullFinish(PlayerHistory.MatchEntry m) {
        for (X01Engine.Leg leg : m.replay().legs()) {
            Dart last = finishingDart(leg, m.me());
            if (last != null && last.equals(Dart.BULL)) return true;
        }
        return false;
    }

    /** The dart that won this leg for {@code player}, or null if they didn't win it. */
    static Dart finishingDart(X01Engine.Leg leg, int player) {
        if (leg.winnerIndex() == null || leg.winnerIndex() != player || leg.turns().isEmpty()) return null;
        X01Engine.Turn last = leg.turns().getLast();
        if (!last.checkout() || last.darts().isEmpty()) return null;
        return last.darts().getLast().dart();
    }
}
