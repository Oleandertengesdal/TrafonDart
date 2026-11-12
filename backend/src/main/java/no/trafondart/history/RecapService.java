package no.trafondart.history;

import no.trafondart.awards.AwardEntities;
import no.trafondart.awards.AwardService;
import no.trafondart.awards.MonthlyAwardRepository;
import no.trafondart.awards.YearAwardService;
import no.trafondart.awards.YearlyAwardRepository;
import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;
import no.trafondart.party.GameType;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The year in darts for one player – a "Wrapped"-style recap (see docs/AWARDS.md). Uses every finished match the
 * player was in, rated or not, plus their party games. Available all year ("so far") and for earlier years.
 */
@Service
public class RecapService {

    public record Record(int played, int won, int lost) {
    }

    public record Rival(PlayerSummary player, int played, int won, int lost) {
    }

    public record MatchHighlight(UUID matchId, PlayerSummary opponent, boolean won, double average, Integer opponentRating, Instant at) {
    }

    public record Finish(String label, int count) {
    }

    public record FavouriteNumber(String label, int count) {
    }

    public record BestMonth(String period, double points, int rank) {
    }

    public record TypeCount(GameType type, int played, int won) {
    }

    public record Games(int played, int won, List<TypeCount> byType) {
    }

    public record Recap(int year, boolean soFar, PlayerSummary player, Record matches, int legsWon, int legsPlayed, long dartsThrown,
                        double average, int count180, int count140, int count100, int highestCheckout, int highestTurn,
                        MatchHighlight bestMatch, MatchHighlight biggestWin, Rival nemesis, Rival favouriteVictim,
                        Finish favouriteFinish, FavouriteNumber favouriteNumber, int longestWinStreak, Integer busiestWeekday,
                        BestMonth bestMonth, int monthsWon, Integer yearRank, Double yearPoints, boolean playerOfTheYear,
                        Games games, List<String> achievements) {
    }

    private final PlayerHistoryService history;
    private final AchievementService achievements;
    private final AwardService awards;
    private final YearAwardService years;
    private final MonthlyAwardRepository monthly;
    private final YearlyAwardRepository yearly;

    public RecapService(PlayerHistoryService history, AchievementService achievements, AwardService awards, YearAwardService years,
                        MonthlyAwardRepository monthly, YearlyAwardRepository yearly) {
        this.history = history;
        this.achievements = achievements;
        this.awards = awards;
        this.years = years;
        this.monthly = monthly;
        this.yearly = yearly;
    }

    @Transactional(readOnly = true)
    public Recap recap(User user, int year) {
        ZoneId zone = awards.zone();
        Instant from = awards.start(YearMonth.of(year, 1));
        Instant to = awards.start(YearMonth.of(year + 1, 1));
        PlayerHistory h = history.load(user, from, to, false);

        int won = 0, legsWon = 0, legsPlayed = 0, c180 = 0, c140 = 0, c100 = 0, hiCo = 0, hiTurn = 0, streak = 0, bestStreak = 0;
        long darts = 0, points = 0, matchDarts = 0;
        MatchHighlight best = null, biggest = null;
        Map<Long, int[]> rivals = new HashMap<>(); // played, won, lost
        Map<Long, PlayerSummary> rivalWho = new HashMap<>();
        Map<String, Integer> finishes = new HashMap<>();
        Map<String, Integer> numbers = new HashMap<>();
        int[] weekdays = new int[8];

        for (PlayerHistory.MatchEntry m : h.matches()) {
            if (m.won()) won++;
            streak = m.won() ? streak + 1 : 0;
            bestStreak = Math.max(bestStreak, streak);
            weekdays[m.at().atZone(zone).getDayOfWeek().getValue()]++;
            if (m.mine() != null) {
                legsWon += m.mine().getLegsWon();
                darts += m.mine().getDartsThrown();
                matchDarts += m.mine().getDartsThrown();
                points += m.mine().getPointsScored();
                c180 += m.mine().getCount180();
                c140 += m.mine().getCount140();
                c100 += m.mine().getCount100();
                hiCo = Math.max(hiCo, m.mine().getHighestCheckout());
                hiTurn = Math.max(hiTurn, m.mine().getHighestTurn());
            }
            legsPlayed += m.legs().size();
            PlayerSummary opp = m.opponent() == null ? null : PlayerSummary.of(m.opponent());
            double avg = round1(m.average());
            Integer oppRating = m.opponentRatingBefore() != null ? Integer.valueOf((int) Math.round(m.opponentRatingBefore()))
                    : opp == null ? null : opp.rating();
            MatchHighlight hl = new MatchHighlight(m.match().getId(), opp, m.won(), avg, oppRating, m.at());
            if (best == null || avg > best.average()) best = hl;
            if (m.won() && oppRating != null && (biggest == null || biggest.opponentRating() == null || oppRating > biggest.opponentRating())) {
                biggest = hl;
            }
            if (opp != null) {
                int[] r = rivals.computeIfAbsent(opp.id(), k -> new int[3]);
                r[0]++;
                r[m.won() ? 1 : 2]++;
                rivalWho.put(opp.id(), opp);
            }
            for (X01Engine.Leg leg : m.replay().legs()) {
                Dart fin = AchievementService.finishingDart(leg, m.me());
                if (fin != null) finishes.merge(fin.label(), 1, Integer::sum);
                for (X01Engine.Turn t : leg.turns()) {
                    if (t.playerIndex() != m.me()) continue;
                    for (X01Engine.DartResult d : t.darts()) {
                        if (!d.dart().isMiss()) numbers.merge(d.dart().segment() == 25 ? "BULL" : String.valueOf(d.dart().segment()), 1, Integer::sum);
                    }
                }
            }
        }

        Map<GameType, int[]> byType = new EnumMap<>(GameType.class);
        int gamesWon = 0;
        for (PlayerHistory.GameEntry g : h.games()) {
            darts += g.darts();
            int[] t = byType.computeIfAbsent(g.type(), k -> new int[2]);
            t[0]++;
            if (g.won()) {
                t[1]++;
                gamesWon++;
            }
            weekdays[g.at().atZone(zone).getDayOfWeek().getValue()]++;
        }
        List<TypeCount> types = new ArrayList<>();
        byType.forEach((type, t) -> types.add(new TypeCount(type, t[0], t[1])));

        Rival nemesis = rivals.entrySet().stream().filter(e -> e.getValue()[2] > 0)
                .sorted(Comparator.<Map.Entry<Long, int[]>>comparingInt(e -> -e.getValue()[2])
                        .thenComparingInt(e -> -(e.getValue()[2] - e.getValue()[1]))
                        .thenComparing(Map.Entry::getKey))
                .findFirst().map(e -> rival(rivalWho.get(e.getKey()), e.getValue())).orElse(null);
        Rival victim = rivals.entrySet().stream().filter(e -> e.getValue()[1] > 0)
                .sorted(Comparator.<Map.Entry<Long, int[]>>comparingInt(e -> -e.getValue()[1])
                        .thenComparingInt(e -> -(e.getValue()[1] - e.getValue()[2]))
                        .thenComparing(Map.Entry::getKey))
                .findFirst().map(e -> rival(rivalWho.get(e.getKey()), e.getValue())).orElse(null);

        Integer busiest = null;
        for (int d = 1; d <= 7; d++) if (weekdays[d] > 0 && (busiest == null || weekdays[d] > weekdays[busiest])) busiest = d;

        // month and year points (rated matches only, as for the awards)
        int lastMonth = year == years.currentYear() ? awards.currentMonth().getMonthValue() : 12;
        BestMonth bestMonth = null;
        for (int mo = 1; mo <= lastMonth; mo++) {
            YearMonth ym = YearMonth.of(year, mo);
            for (AwardService.Standing s : awards.standings(ym)) {
                if (!s.player().id().equals(user.getId())) continue;
                double p = s.points().total();
                if (bestMonth == null || p > bestMonth.points()) bestMonth = new BestMonth(ym.toString(), round1(p), s.rank());
            }
        }
        List<String> periods = new ArrayList<>();
        for (int mo = 1; mo <= 12; mo++) periods.add(YearMonth.of(year, mo).toString());
        int monthsWon = (int) monthly.findByPeriods(periods).stream()
                .filter(a -> a.getAward() == AwardEntities.Award.PLAYER_OF_MONTH && a.getUserId().equals(user.getId())).count();
        Integer yearRank = null;
        Double yearPoints = null;
        for (YearAwardService.YearStanding s : years.standings(year)) {
            if (!s.player().id().equals(user.getId())) continue;
            yearRank = s.eligible() ? s.rank() : null;
            yearPoints = s.points();
        }
        boolean poy = yearly.findByUser(user.getId()).stream().anyMatch(a -> a.getYear() == year);

        List<String> unlocked = achievements.cabinet(user).items().stream()
                .filter(a -> a.unlockedAt() != null && !a.unlockedAt().isBefore(from) && a.unlockedAt().isBefore(to))
                .map(AchievementService.Achievement::code).toList();

        return new Recap(year, year == years.currentYear(), PlayerSummary.of(user),
                new Record(h.matches().size(), won, h.matches().size() - won), legsWon, legsPlayed, darts,
                matchDarts == 0 ? 0 : round2(points * 3.0 / matchDarts), c180, c140, c100, hiCo, hiTurn, best, biggest, nemesis, victim,
                top(finishes, Finish::new), top(numbers, FavouriteNumber::new), bestStreak, busiest, bestMonth, monthsWon, yearRank, yearPoints, poy,
                new Games(h.games().size(), gamesWon, types), unlocked);
    }

    private static Rival rival(PlayerSummary p, int[] r) {
        return new Rival(p, r[0], r[1], r[2]);
    }

    private static <T> T top(Map<String, Integer> counts, java.util.function.BiFunction<String, Integer, T> make) {
        return new LinkedHashMap<>(counts).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .findFirst().map(e -> make.apply(e.getKey(), e.getValue())).orElse(null);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
