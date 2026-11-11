package no.trafondart.match;

import no.trafondart.game.CheckoutCalculator;
import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;
import no.trafondart.social.SocialService;
import no.trafondart.tournament.Tournament;
import no.trafondart.tournament.TournamentRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns engine state and entities into API views. */
@Component
public class MatchViewMapper {

    private final UserRepository users;
    private final TournamentRepository tournaments;
    private final DartRepository darts;
    private final PlayerMatchStatsRepository statsRepo;
    private final DisputeRepository disputes;
    private final SocialService social;

    public MatchViewMapper(UserRepository users, TournamentRepository tournaments, DartRepository darts,
                           PlayerMatchStatsRepository statsRepo, DisputeRepository disputes, SocialService social) {
        this.social = social;
        this.users = users;
        this.tournaments = tournaments;
        this.darts = darts;
        this.statsRepo = statsRepo;
        this.disputes = disputes;
    }

    public X01Engine.MatchState replay(Match match) {
        List<Dart> list = darts.findAllByMatchIdOrderBySeqAsc(match.getId()).stream().map(ThrownDart::toDart).toList();
        return X01Engine.replay(match.config(), match.getStartingPlayer(), list);
    }

    public MatchViews.LiveMatchView live(Match match) {
        return live(match, replay(match));
    }

    public MatchViews.LiveMatchView live(Match match, X01Engine.MatchState s) {
        User p1 = users.findById(match.getPlayer1Id()).orElseThrow();
        User p2 = users.findById(match.getPlayer2Id()).orElseThrow();
        List<Long> scorers = new ArrayList<>(List.of(match.getPlayer1Id(), match.getPlayer2Id()));
        if (!scorers.contains(match.getCreatedBy())) scorers.add(match.getCreatedBy());

        boolean inProgress = match.getStatus() == Match.Status.IN_PROGRESS;
        List<String> checkout = null;
        if (inProgress && !s.finished() && s.opened()[s.currentPlayerIndex()]) {
            checkout = CheckoutCalculator.suggest(s.remaining()[s.currentPlayerIndex()], s.dartsLeftInTurn(),
                    match.getOutRule()).map(r -> r.stream().map(Dart::label).toList()).orElse(null);
        }

        List<MatchViews.LivePlayerStats> stats = new ArrayList<>();
        for (X01Engine.PlayerStats ps : s.stats()) {
            stats.add(new MatchViews.LivePlayerStats(round2(ps.threeDartAverage()), round2(ps.first9Average()),
                    ps.dartsThrown(), ps.pointsScored(), ps.count180(), ps.count140(), ps.count100(),
                    ps.highestTurn(), ps.highestCheckout(), ps.checkoutAttempts(), ps.checkoutsHit(),
                    round2(ps.checkoutPercentage())));
        }

        List<MatchViews.LegView> legs = s.legs().stream()
                .map(l -> new MatchViews.LegView(l.legNo(), l.starterIndex(), l.winnerIndex(),
                        l.winnerIndex() == null ? 0 : l.dartsThrownBy(l.winnerIndex()), l.checkoutScore()))
                .toList();

        // The engine clears the current turn whenever a turn ends (3 darts, bust, leg or match won).
        boolean turnJustEnded = s.dartCount() > 0 && s.currentTurn() == null;

        return new MatchViews.LiveMatchView(
                match.getId(),
                match.getStatus().name(),
                MatchViews.ConfigView.of(match.config()),
                match.isRated(),
                tournamentRef(match.getTournamentId()),
                List.of(PlayerSummary.of(p1), PlayerSummary.of(p2)),
                scorers,
                s.currentLeg().legNo(),
                s.currentPlayerIndex(),
                s.legsWon(),
                s.remaining(),
                s.opened(),
                turnView(s.currentTurn()),
                turnView(s.lastCompletedTurn()),
                turnJustEnded,
                stats,
                checkout,
                s.dartsLeftInTurn(),
                s.lastEvent().name(),
                s.winnerIndex(),
                s.dartCount(),
                legs,
                match.getCreatedAt(),
                match.getCompletedAt(),
                disputes.existsByMatchIdAndStatus(match.getId(), MatchRecords.Dispute.Status.OPEN));
    }

    private static MatchViews.TurnView turnView(X01Engine.Turn t) {
        if (t == null) return null;
        List<MatchViews.DartView> ds = t.darts().stream()
                .map(r -> new MatchViews.DartView(r.dart().segment(), r.dart().multiplier(), r.dart().label(), r.counted()))
                .toList();
        return new MatchViews.TurnView(t.playerIndex(), ds, t.bust() ? 0 : t.runningTotal(), t.bust(), t.checkout());
    }

    /** Summaries for many matches with batched lookups (no N+1). Live matches are replayed for their score. */
    public List<MatchViews.MatchSummary> summaries(Collection<Match> matches) {
        if (matches.isEmpty()) return List.of();
        Set<Long> userIds = new HashSet<>();
        Set<UUID> tournamentIds = new HashSet<>();
        List<UUID> completedIds = new ArrayList<>();
        for (Match m : matches) {
            userIds.add(m.getPlayer1Id());
            userIds.add(m.getPlayer2Id());
            if (m.getTournamentId() != null) tournamentIds.add(m.getTournamentId());
            if (m.getStatus() != Match.Status.IN_PROGRESS) completedIds.add(m.getId());
        }
        Map<Long, User> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Tournament> tById = tournaments.findAllById(tournamentIds).stream()
                .collect(Collectors.toMap(Tournament::getId, Function.identity()));
        Map<UUID, SocialService.Counts> socialCounts = social.counts(matches.stream().map(Match::getId).toList());
        Map<String, MatchRecords.PlayerMatchStats> statsByKey = new HashMap<>();
        for (MatchRecords.PlayerMatchStats st : statsRepo.findAllByIdMatchIdIn(completedIds)) {
            statsByKey.put(st.getId().getMatchId() + ":" + st.getId().getUserId(), st);
        }

        List<MatchViews.MatchSummary> out = new ArrayList<>();
        for (Match m : matches) {
            int[] legsWon = new int[2];
            double[] averages = new double[2];
            if (m.getStatus() == Match.Status.IN_PROGRESS) {
                X01Engine.MatchState s = replay(m);
                legsWon = s.legsWon();
                for (int i = 0; i < 2; i++) averages[i] = round2(s.stats()[i].threeDartAverage());
            } else {
                for (int i = 0; i < 2; i++) {
                    MatchRecords.PlayerMatchStats st = statsByKey.get(m.getId() + ":" + m.playerId(i));
                    if (st != null) {
                        legsWon[i] = st.getLegsWon();
                        averages[i] = st.getDartsThrown() == 0 ? 0 : round2(st.getPointsScored() * 3.0 / st.getDartsThrown());
                    }
                }
            }
            Tournament t = m.getTournamentId() == null ? null : tById.get(m.getTournamentId());
            Integer winnerIndex = m.getWinnerId() == null ? null : m.indexOf(m.getWinnerId());
            out.add(new MatchViews.MatchSummary(m.getId(), m.getStatus().name(), MatchViews.ConfigView.of(m.config()),
                    m.isRated(),
                    List.of(PlayerSummary.of(userById.get(m.getPlayer1Id())), PlayerSummary.of(userById.get(m.getPlayer2Id()))),
                    legsWon, averages, winnerIndex, m.getCreatedAt(), m.getCompletedAt(),
                    t == null ? null : new MatchViews.TournamentRef(t.getId(), t.getName()),
                    socialCounts.getOrDefault(m.getId(), new SocialService.Counts(0, 0)).kudos(),
                    socialCounts.getOrDefault(m.getId(), new SocialService.Counts(0, 0)).comments()));
        }
        return out;
    }

    private MatchViews.TournamentRef tournamentRef(UUID id) {
        if (id == null) return null;
        return tournaments.findById(id).map(t -> new MatchViews.TournamentRef(t.getId(), t.getName())).orElse(null);
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
