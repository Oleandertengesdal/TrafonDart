package no.trafondart.analysis;

import no.trafondart.common.ApiException;
import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;
import no.trafondart.match.DartRepository;
import no.trafondart.match.Match;
import no.trafondart.match.MatchRepository;
import no.trafondart.match.ThrownDart;
import no.trafondart.rating.Glicko2;
import no.trafondart.rating.RatingHistory;
import no.trafondart.rating.RatingHistoryRepository;
import no.trafondart.social.KudosRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static no.trafondart.match.MatchViewMapper.round2;

/**
 * Deep-dive statistics computed by replaying the dart log: a turn-by-turn match breakdown and the
 * "dart nerd" section of a player profile. At friend-group scale replaying every match takes milliseconds.
 */
@Service
public class AnalyticsService {

    public record TurnRow(int playerIndex, int turnInLeg, List<String> darts, int scored, int remainingAfter,
                          boolean bust, boolean checkout) {
    }

    public record LegBreakdown(int legNo, int starterIndex, Integer winnerIndex, int[] dartsUsed, double[] averages,
                               List<TurnRow> turns) {
    }

    public record PlayerAnalysis(List<DartAnalytics.HeatCell> heatmap, List<DartAnalytics.Band> bands,
                                 double trebleRate, double doubleRate, double missRate, int busts,
                                 Integer bestLeg, List<DartAnalytics.LabelCount> finishes) {
    }

    public record MatchBreakdown(UUID matchId, double[] winProbability, List<LegBreakdown> legs,
                                 List<PlayerAnalysis> players) {
    }

    public record TopCheckout(int score, String finisher, UUID matchId, Instant at) {
    }

    public record DartStats(int dartsThrown, int pointsScored, int turns, int legsPlayed, int legsWon,
                            double trebleRate, double doubleRate, double bullRate, double missRate,
                            int busts, double bustRate, Integer bestLeg, double avgDartsPerLegWon,
                            DartAnalytics.Rate hold, DartAnalytics.Rate breaks, Integer mostCommonTurn,
                            Integer favouriteSegment, List<DartAnalytics.Band> bands,
                            List<DartAnalytics.HeatCell> heatmap, List<DartAnalytics.LabelCount> finishes,
                            List<TopCheckout> topCheckouts, long kudosReceived) {
    }

    private final MatchRepository matches;
    private final DartRepository darts;
    private final UserRepository users;
    private final RatingHistoryRepository ratingHistory;
    private final KudosRepository kudos;

    public AnalyticsService(MatchRepository matches, DartRepository darts, UserRepository users,
                            RatingHistoryRepository ratingHistory, KudosRepository kudos) {
        this.matches = matches;
        this.darts = darts;
        this.users = users;
        this.ratingHistory = ratingHistory;
        this.kudos = kudos;
    }

    @Transactional(readOnly = true)
    public MatchBreakdown breakdown(UUID matchId) {
        Match m = matches.findById(matchId).orElseThrow(ApiException::notFound);
        List<Dart> list = darts.findAllByMatchIdOrderBySeqAsc(matchId).stream().map(ThrownDart::toDart).toList();
        X01Engine.MatchState state = X01Engine.replay(m.config(), m.getStartingPlayer(), list);

        List<LegBreakdown> legs = new ArrayList<>();
        for (X01Engine.Leg leg : state.legs()) {
            if (leg.turns().isEmpty()) continue;
            int[] used = new int[2];
            int[] pts = new int[2];
            List<TurnRow> rows = new ArrayList<>();
            for (X01Engine.Turn t : leg.turns()) {
                used[t.playerIndex()] += t.darts().size();
                pts[t.playerIndex()] += t.scored();
                rows.add(new TurnRow(t.playerIndex(), t.turnInLeg(),
                        t.darts().stream().map(r -> r.dart().label()).toList(),
                        t.bust() ? 0 : t.runningTotal(), t.endRemaining(), t.bust(), t.checkout()));
            }
            double[] avg = {used[0] == 0 ? 0 : round2(pts[0] * 3.0 / used[0]), used[1] == 0 ? 0 : round2(pts[1] * 3.0 / used[1])};
            legs.add(new LegBreakdown(leg.legNo(), leg.starterIndex(), leg.winnerIndex(), used, avg, rows));
        }

        List<PlayerAnalysis> players = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            DartAnalytics.Accumulator a = new DartAnalytics.Accumulator();
            a.addMatch(state, i);
            players.add(new PlayerAnalysis(a.heatmap(), a.bands(), a.trebleRate(), a.doubleRate(), a.missRate(),
                    a.busts(), a.bestLeg(), a.finishes(5)));
        }
        return new MatchBreakdown(matchId, preMatchWinProbability(m), legs, players);
    }

    /** Win probability from the ratings both players had when the match started (current ratings if unrated/live). */
    private double[] preMatchWinProbability(Match m) {
        Glicko2.Rating[] r = new Glicko2.Rating[2];
        Map<Long, RatingHistory> before = ratingHistory.findAllByMatchId(m.getId()).stream()
                .collect(Collectors.toMap(RatingHistory::getUserId, h -> h, (a, b) -> a));
        for (int i = 0; i < 2; i++) {
            Long uid = m.playerId(i);
            RatingHistory h = before.get(uid);
            if (h != null) {
                r[i] = new Glicko2.Rating(h.getRatingBefore(), h.getRdBefore(), Glicko2.DEFAULT_VOLATILITY);
            } else {
                r[i] = users.findById(uid).map(User::glicko).orElse(Glicko2.Rating.initial());
            }
        }
        double p0 = Glicko2.winProbability(r[0], r[1]);
        return new double[]{round2(p0 * 100), round2((1 - p0) * 100)};
    }

    @Transactional(readOnly = true)
    public DartStats dartStats(String username) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        List<Match> played = matches.findCompletedFor(user.getId());
        Map<UUID, List<Dart>> byMatch = darts.findAllByMatchIdInOrderByMatchIdAscSeqAsc(played.stream().map(Match::getId).toList())
                .stream().collect(Collectors.groupingBy(ThrownDart::getMatchId,
                        Collectors.mapping(ThrownDart::toDart, Collectors.toList())));

        DartAnalytics.Accumulator acc = new DartAnalytics.Accumulator();
        List<TopCheckout> checkouts = new ArrayList<>();
        for (Match m : played) {
            int me = m.indexOf(user.getId());
            X01Engine.MatchState s = X01Engine.replay(m.config(), m.getStartingPlayer(), byMatch.getOrDefault(m.getId(), List.of()));
            acc.addMatch(s, me);
            for (X01Engine.Leg leg : s.legs()) {
                if (leg.winnerIndex() == null || leg.winnerIndex() != me) continue;
                X01Engine.Turn last = leg.turns().getLast();
                checkouts.add(new TopCheckout(last.scored(), last.darts().getLast().dart().label(), m.getId(), m.getCompletedAt()));
            }
        }
        checkouts.sort(Comparator.comparingInt(TopCheckout::score).reversed().thenComparing(TopCheckout::at,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new DartStats(acc.darts(), acc.points(), acc.turns(), acc.legsPlayed(), acc.legsWon(),
                acc.trebleRate(), acc.doubleRate(), acc.bullRate(), acc.missRate(), acc.busts(), acc.bustRate(),
                acc.bestLeg(), acc.avgDartsPerLegWon(), acc.hold(), acc.breaks(), acc.mostCommonTurn(),
                acc.favouriteSegment(), acc.bands(), acc.heatmap(), acc.finishes(6),
                checkouts.subList(0, Math.min(5, checkouts.size())), kudos.countReceivedBy(user.getId()));
    }
}
