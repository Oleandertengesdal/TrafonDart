package no.trafondart.stats;

import no.trafondart.common.ApiException;
import no.trafondart.match.Match;
import no.trafondart.match.MatchRecords.PlayerMatchStats;
import no.trafondart.match.MatchRepository;
import no.trafondart.match.PlayerMatchStatsRepository;
import no.trafondart.rating.RatingHistory;
import no.trafondart.rating.RatingHistoryRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static no.trafondart.match.MatchViewMapper.round2;

/** Profile dashboards, head-to-head records and the leaderboard. Everything is derived from completed matches. */
@Service
public class StatsService {

    private static final int TREND_LENGTH = 30;

    private final UserRepository users;
    private final MatchRepository matches;
    private final PlayerMatchStatsRepository stats;
    private final RatingHistoryRepository ratingHistory;

    public StatsService(UserRepository users, MatchRepository matches, PlayerMatchStatsRepository stats,
                        RatingHistoryRepository ratingHistory) {
        this.users = users;
        this.matches = matches;
        this.stats = stats;
        this.ratingHistory = ratingHistory;
    }

    /** A user's completed matches with both players' stat rows, oldest first. */
    private record Played(Match match, PlayerMatchStats mine, PlayerMatchStats theirs, boolean won) {
    }

    private List<Played> played(Long userId) {
        List<Match> list = matches.findCompletedFor(userId);
        Map<String, PlayerMatchStats> rows = new HashMap<>();
        for (PlayerMatchStats s : stats.findAllByIdMatchIdIn(list.stream().map(Match::getId).toList())) {
            rows.put(s.getId().getMatchId() + ":" + s.getId().getUserId(), s);
        }
        return list.stream()
                .sorted(Comparator.comparing(Match::getCompletedAt))
                .map(m -> new Played(m, rows.get(m.getId() + ":" + userId), rows.get(m.getId() + ":" + m.opponentOf(userId)),
                        userId.equals(m.getWinnerId())))
                .filter(p -> p.mine() != null && p.theirs() != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatsViews.ProfileStats profile(String username) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        List<Played> games = played(user.getId());
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));

        int won = 0, legsWon = 0, legsLost = 0, darts = 0, points = 0, f9d = 0, f9p = 0;
        int c180 = 0, c140 = 0, c100 = 0, highestTurn = 0, highestCheckout = 0, attempts = 0, hits = 0;
        double bestAvg = 0;
        List<StatsViews.AveragePoint> trend = new ArrayList<>();
        for (Played p : games) {
            PlayerMatchStats s = p.mine();
            if (p.won()) won++;
            legsWon += s.getLegsWon();
            legsLost += p.theirs().getLegsWon();
            darts += s.getDartsThrown();
            points += s.getPointsScored();
            f9d += s.getFirst9Darts();
            f9p += s.getFirst9Points();
            c180 += s.getCount180();
            c140 += s.getCount140();
            c100 += s.getCount100();
            highestTurn = Math.max(highestTurn, s.getHighestTurn());
            highestCheckout = Math.max(highestCheckout, s.getHighestCheckout());
            attempts += s.getCheckoutAttempts();
            hits += s.getCheckoutsHit();
            double avg = s.getDartsThrown() == 0 ? 0 : s.getPointsScored() * 3.0 / s.getDartsThrown();
            double f9 = s.getFirst9Darts() == 0 ? 0 : s.getFirst9Points() * 3.0 / s.getFirst9Darts();
            bestAvg = Math.max(bestAvg, avg);
            trend.add(new StatsViews.AveragePoint(p.match().getCompletedAt(), p.match().getId(), round2(avg), round2(f9), p.won()));
        }
        int n = games.size();

        List<String> form = new ArrayList<>();
        for (int i = n - 1; i >= 0 && form.size() < 10; i--) form.add(games.get(i).won() ? "W" : "L");
        int streak = 0;
        if (n > 0) {
            boolean last = games.get(n - 1).won();
            for (int i = n - 1; i >= 0 && games.get(i).won() == last; i--) streak++;
            if (!last) streak = -streak;
        }

        Map<UUID, Match> matchById = games.stream().collect(Collectors.toMap(p -> p.match().getId(), Played::match));
        List<StatsViews.RatingPoint> history = new ArrayList<>();
        int peak = (int) Math.round(user.getRating());
        for (RatingHistory h : ratingHistory.findAllByUserIdOrderByRatedAtAscIdAsc(user.getId())) {
            Match m = matchById.get(h.getMatchId());
            String opponent = m == null ? null : userById.get(m.opponentOf(user.getId())).getUsername();
            int r = (int) Math.round(h.getRatingAfter());
            peak = Math.max(peak, r);
            history.add(new StatsViews.RatingPoint(h.getRatedAt(), r, (int) Math.round(h.getRdAfter()), h.getMatchId(),
                    opponent, m != null && user.getId().equals(m.getWinnerId())));
        }

        return new StatsViews.ProfileStats(
                PlayerSummary.of(user), user.getCreatedAt(),
                n, won, n - won, n == 0 ? 0 : round2(won * 100.0 / n),
                legsWon, legsLost, legsWon + legsLost == 0 ? 0 : round2(legsWon * 100.0 / (legsWon + legsLost)),
                darts == 0 ? 0 : round2(points * 3.0 / darts),
                f9d == 0 ? 0 : round2(f9p * 3.0 / f9d),
                round2(bestAvg),
                c180, c140, c100, highestTurn, highestCheckout, attempts, hits,
                attempts == 0 ? 0 : round2(hits * 100.0 / attempts),
                peak, form, streak, history,
                trend.subList(Math.max(0, trend.size() - TREND_LENGTH), trend.size()));
    }

    @Transactional(readOnly = true)
    public List<StatsViews.HeadToHeadRow> headToHead(String username) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        return headToHeadRows(user, null);
    }

    @Transactional(readOnly = true)
    public StatsViews.HeadToHeadRow headToHead(String username, String other) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        User opp = users.findByUsernameIgnoreCase(other).orElseThrow(ApiException::notFound);
        List<StatsViews.HeadToHeadRow> rows = headToHeadRows(user, opp.getId());
        return rows.isEmpty()
                ? new StatsViews.HeadToHeadRow(PlayerSummary.of(opp), 0, 0, 0, 0, 0, 0, 0, null)
                : rows.getFirst();
    }

    private List<StatsViews.HeadToHeadRow> headToHeadRows(User user, Long onlyOpponent) {
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, int[]> acc = new HashMap<>();   // played, won, legsWon, legsLost, myDarts, myPoints, theirDarts, theirPoints
        Map<Long, Instant> last = new HashMap<>();
        for (Played p : played(user.getId())) {
            Long opp = p.match().opponentOf(user.getId());
            if (onlyOpponent != null && !onlyOpponent.equals(opp)) continue;
            int[] a = acc.computeIfAbsent(opp, k -> new int[8]);
            a[0]++;
            if (p.won()) a[1]++;
            a[2] += p.mine().getLegsWon();
            a[3] += p.theirs().getLegsWon();
            a[4] += p.mine().getDartsThrown();
            a[5] += p.mine().getPointsScored();
            a[6] += p.theirs().getDartsThrown();
            a[7] += p.theirs().getPointsScored();
            last.merge(opp, p.match().getCompletedAt(), (x, y) -> x.isAfter(y) ? x : y);
        }
        return acc.entrySet().stream()
                .map(e -> {
                    int[] a = e.getValue();
                    return new StatsViews.HeadToHeadRow(PlayerSummary.of(userById.get(e.getKey())), a[0], a[1], a[0] - a[1],
                            a[2], a[3], a[4] == 0 ? 0 : round2(a[5] * 3.0 / a[4]), a[6] == 0 ? 0 : round2(a[7] * 3.0 / a[6]),
                            last.get(e.getKey()));
                })
                .sorted(Comparator.comparingInt(StatsViews.HeadToHeadRow::played).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatsViews.LeaderboardRow> leaderboard() {
        List<User> all = users.findAllByOrderByRatingDesc().stream().filter(User::isEnabled).toList();
        List<StatsViews.LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (User u : all) {
            List<Played> games = played(u.getId());
            int won = (int) games.stream().filter(Played::won).count();
            int darts = games.stream().mapToInt(p -> p.mine().getDartsThrown()).sum();
            int points = games.stream().mapToInt(p -> p.mine().getPointsScored()).sum();
            int c180 = games.stream().mapToInt(p -> p.mine().getCount180()).sum();
            int hc = games.stream().mapToInt(p -> p.mine().getHighestCheckout()).max().orElse(0);
            rows.add(new StatsViews.LeaderboardRow(rank++, PlayerSummary.of(u), games.size(), won,
                    games.isEmpty() ? 0 : round2(won * 100.0 / games.size()),
                    darts == 0 ? 0 : round2(points * 3.0 / darts), c180, hc));
        }
        return rows;
    }
}
