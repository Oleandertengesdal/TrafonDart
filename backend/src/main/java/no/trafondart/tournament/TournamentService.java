package no.trafondart.tournament;

import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.game.GameConfig;
import no.trafondart.match.Match;
import no.trafondart.match.MatchEvents;
import no.trafondart.match.MatchRecords;
import no.trafondart.match.MatchRepository;
import no.trafondart.match.MatchService;
import no.trafondart.match.MatchViews;
import no.trafondart.match.PlayerMatchStatsRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tournament lifecycle: draft → started (fixtures generated) → fixtures played → completed.
 * Progression is driven by match lifecycle events, so finishing a match automatically fills in the bracket.
 */
@Service
public class TournamentService {

    /** Broadcast after commit: clients refetch the tournament. */
    public record TournamentUpdated(UUID tournamentId) {
    }

    private final TournamentRepository tournaments;
    private final TournamentParticipantRepository participants;
    private final TournamentFixtureRepository fixtures;
    private final UserRepository users;
    private final MatchService matchService;
    private final MatchRepository matches;
    private final PlayerMatchStatsRepository stats;
    private final ApplicationEventPublisher events;

    public TournamentService(TournamentRepository tournaments, TournamentParticipantRepository participants,
                             TournamentFixtureRepository fixtures, UserRepository users, MatchService matchService,
                             MatchRepository matches, PlayerMatchStatsRepository stats,
                             ApplicationEventPublisher events) {
        this.tournaments = tournaments;
        this.participants = participants;
        this.fixtures = fixtures;
        this.users = users;
        this.matchService = matchService;
        this.matches = matches;
        this.stats = stats;
        this.events = events;
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public TournamentViews.TournamentDetail create(AuthUser actor, TournamentViews.CreateTournamentRequest req) {
        GameConfig config = new GameConfig(req.startScore(), req.inRule(), req.outRule(), req.legsToWin());
        Tournament t = tournaments.save(new Tournament(req.name().trim(), req.format(), config,
                req.rated() == null || req.rated(), actor.id()));
        setParticipants(t, req.participantIds());
        return detail(t.getId());
    }

    @Transactional
    public TournamentViews.TournamentDetail updateParticipants(UUID id, AuthUser actor, List<Long> ids) {
        Tournament t = manageable(id, actor);
        if (t.getStatus() != Tournament.Status.DRAFT) throw new ApiException(ErrorCode.TOURNAMENT_NOT_DRAFT);
        setParticipants(t, ids);
        events.publishEvent(new TournamentUpdated(id));
        return detail(id);
    }

    private void setParticipants(Tournament t, List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        if (unique.size() > TournamentViews.MAX_PARTICIPANTS) throw new ApiException(ErrorCode.TOURNAMENT_TOO_MANY_PLAYERS);
        List<User> found = users.findAllById(unique);
        if (found.size() != unique.size() || found.stream().anyMatch(u -> !u.isEnabled())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        participants.deleteByTournament(t.getId());
        int seed = 1;
        for (Long uid : unique) participants.save(new TournamentParticipant(t.getId(), uid, seed++));
    }

    @Transactional
    public TournamentViews.TournamentDetail start(UUID id, AuthUser actor) {
        Tournament t = manageable(id, actor);
        if (t.getStatus() != Tournament.Status.DRAFT) throw new ApiException(ErrorCode.TOURNAMENT_NOT_DRAFT);
        List<TournamentParticipant> entrants = participants.findByTournament(id);
        if (entrants.size() < 2) throw new ApiException(ErrorCode.TOURNAMENT_TOO_FEW_PLAYERS);

        // Seed by current rating, best first.
        Map<Long, User> userById = users.findAllById(entrants.stream().map(TournamentParticipant::userId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> seeded = entrants.stream().map(TournamentParticipant::userId)
                .sorted(Comparator.comparingDouble((Long uid) -> userById.get(uid).getRating()).reversed())
                .toList();
        for (TournamentParticipant p : entrants) p.setSeed(seeded.indexOf(p.userId()) + 1);

        if (t.getFormat() == Tournament.Format.ROUND_ROBIN) {
            for (BracketGenerator.Slot s : BracketGenerator.roundRobin(seeded)) {
                TournamentFixture f = new TournamentFixture(id, s.round(), s.position(), s.player1(), s.player2());
                f.setStatus(TournamentFixture.Status.READY);
                fixtures.save(f);
            }
        } else {
            buildKnockout(t, seeded);
        }
        t.start();
        events.publishEvent(new TournamentUpdated(id));
        return detail(id);
    }

    private void buildKnockout(Tournament t, List<Long> seeded) {
        List<BracketGenerator.Slot> slots = BracketGenerator.knockout(seeded);
        Map<String, TournamentFixture> byPos = new HashMap<>();
        for (BracketGenerator.Slot s : slots) {
            TournamentFixture f = fixtures.save(new TournamentFixture(t.getId(), s.round(), s.position(), s.player1(), s.player2()));
            byPos.put(s.round() + ":" + s.position(), f);
        }
        for (TournamentFixture f : byPos.values()) {
            TournamentFixture next = byPos.get((f.getRound() + 1) + ":" + (f.getPosition() / 2));
            if (next != null) f.linkNext(next.getId(), f.getPosition() % 2 + 1);
        }
        // Resolve byes, then mark fixtures with two known players as playable.
        for (BracketGenerator.Slot s : slots) {
            TournamentFixture f = byPos.get(s.round() + ":" + s.position());
            if (s.bye()) {
                Long advancing = f.getPlayer1Id() != null ? f.getPlayer1Id() : f.getPlayer2Id();
                f.setWinnerId(advancing);
                f.setStatus(TournamentFixture.Status.BYE);
                advance(f, advancing);
            }
        }
        for (TournamentFixture f : byPos.values()) {
            if (f.getStatus() == TournamentFixture.Status.PENDING && f.hasBothPlayers()) {
                f.setStatus(TournamentFixture.Status.READY);
            }
        }
    }

    /** Creates the match for a ready fixture. Any participant of the fixture, the organiser or an admin may start it. */
    @Transactional
    public TournamentViews.StartedFixture startFixture(UUID tournamentId, Long fixtureId, AuthUser actor) {
        Tournament t = tournaments.findByIdForUpdate(tournamentId).orElseThrow(ApiException::notFound);
        if (t.getStatus() != Tournament.Status.IN_PROGRESS) throw new ApiException(ErrorCode.TOURNAMENT_NOT_IN_PROGRESS);
        TournamentFixture f = fixtures.findById(fixtureId)
                .filter(x -> x.getTournamentId().equals(tournamentId))
                .orElseThrow(ApiException::notFound);
        if (f.getStatus() == TournamentFixture.Status.IN_PROGRESS && f.getMatchId() != null) {
            return new TournamentViews.StartedFixture(f.getMatchId()); // idempotent: someone already started it
        }
        if (f.getStatus() != TournamentFixture.Status.READY) throw new ApiException(ErrorCode.FIXTURE_NOT_READY);
        if (!(f.involves(actor.id()) || t.getCreatedBy().equals(actor.id()) || actor.isAdmin())) {
            throw ApiException.forbidden();
        }
        Match m = matchService.createInternal(t.config(), f.getPlayer1Id(), f.getPlayer2Id(), null, t.isRated(),
                actor.id(), t.getId());
        f.setMatchId(m.getId());
        f.setStatus(TournamentFixture.Status.IN_PROGRESS);
        events.publishEvent(new TournamentUpdated(tournamentId));
        return new TournamentViews.StartedFixture(m.getId());
    }

    @Transactional
    public TournamentViews.TournamentDetail cancel(UUID id, AuthUser actor) {
        Tournament t = manageable(id, actor);
        if (t.getStatus() == Tournament.Status.COMPLETED || t.getStatus() == Tournament.Status.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        t.cancel();
        events.publishEvent(new TournamentUpdated(id));
        return detail(id);
    }

    private Tournament manageable(UUID id, AuthUser actor) {
        Tournament t = tournaments.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        if (!t.getCreatedBy().equals(actor.id()) && !actor.isAdmin()) throw ApiException.forbidden();
        return t;
    }

    // ------------------------------------------------------------------ progression (match lifecycle events)

    @EventListener
    public void onMatchCompleted(MatchEvents.MatchCompleted event) {
        Match m = event.match();
        fixtures.findByMatchId(m.getId()).ifPresent(f -> {
            Tournament t = tournaments.findByIdForUpdate(f.getTournamentId()).orElseThrow();
            if (t.getStatus() != Tournament.Status.IN_PROGRESS) return;
            f.setWinnerId(m.getWinnerId());
            f.setStatus(TournamentFixture.Status.COMPLETED);
            advance(f, m.getWinnerId());
            fixtures.flush();
            checkCompletion(t);
            events.publishEvent(new TournamentUpdated(t.getId()));
        });
    }

    @EventListener
    public void onMatchAbandoned(MatchEvents.MatchAbandoned event) {
        fixtures.findByMatchId(event.match().getId()).ifPresent(f -> {
            f.setMatchId(null);
            f.setStatus(TournamentFixture.Status.READY);
            events.publishEvent(new TournamentUpdated(f.getTournamentId()));
        });
    }

    /** An admin voided a match after a dispute: undo its effect on the tournament if nothing depends on it yet. */
    @EventListener
    public void onMatchVoided(MatchEvents.MatchVoided event) {
        fixtures.findByMatchId(event.match().getId()).ifPresent(f -> {
            Tournament t = tournaments.findByIdForUpdate(f.getTournamentId()).orElseThrow();
            if (f.getNextFixtureId() != null && f.getWinnerId() != null) {
                TournamentFixture next = fixtures.findById(f.getNextFixtureId()).orElseThrow();
                if (next.getMatchId() != null || next.getStatus() == TournamentFixture.Status.COMPLETED) {
                    throw new ApiException(ErrorCode.DISPUTE_TOURNAMENT_LOCKED);
                }
                next.setSlot(f.getNextSlot(), null);
                next.setStatus(TournamentFixture.Status.PENDING);
            }
            f.setWinnerId(null);
            f.setMatchId(null);
            f.setStatus(TournamentFixture.Status.READY);
            if (t.getStatus() == Tournament.Status.COMPLETED) t.reopen();
            events.publishEvent(new TournamentUpdated(t.getId()));
        });
    }

    private void advance(TournamentFixture f, Long winnerId) {
        if (f.getNextFixtureId() == null) return;
        TournamentFixture next = fixtures.findById(f.getNextFixtureId()).orElseThrow();
        next.setSlot(f.getNextSlot(), winnerId);
        if (next.hasBothPlayers() && next.getStatus() == TournamentFixture.Status.PENDING) {
            next.setStatus(TournamentFixture.Status.READY);
        }
    }

    private void checkCompletion(Tournament t) {
        List<TournamentFixture> all = fixtures.findAllByTournamentIdOrderByRoundAscPositionAsc(t.getId());
        boolean done = all.stream().allMatch(f -> f.getStatus() == TournamentFixture.Status.COMPLETED
                || f.getStatus() == TournamentFixture.Status.BYE);
        if (!done) return;
        if (t.getFormat() == Tournament.Format.SINGLE_ELIMINATION) {
            TournamentFixture fin = all.stream().max(Comparator.comparingInt(TournamentFixture::getRound)).orElseThrow();
            t.complete(fin.getWinnerId());
        } else {
            List<TournamentViews.StandingRow> table = standings(t, all, playerMap(t.getId()));
            t.complete(table.isEmpty() ? null : table.getFirst().player().id());
        }
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<TournamentViews.TournamentSummary> list() {
        List<Tournament> all = tournaments.findAllByOrderByCreatedAtDesc();
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return all.stream().map(t -> summary(t, userById, (int) participants.countByTournament(t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TournamentViews.TournamentDetail detail(UUID id) {
        Tournament t = tournaments.findById(id).orElseThrow(ApiException::notFound);
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, PlayerSummary> players = playerMap(id);
        List<TournamentParticipant> entrants = participants.findByTournament(id);
        List<TournamentFixture> all = fixtures.findAllByTournamentIdOrderByRoundAscPositionAsc(id);

        Map<UUID, Match> matchById = matches.findAllById(all.stream().map(TournamentFixture::getMatchId)
                .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Match::getId, Function.identity()));
        Map<String, Integer> legs = legsByMatchAndUser(matchById.keySet());

        List<TournamentViews.FixtureView> fixtureViews = all.stream().map(f -> {
            Match m = f.getMatchId() == null ? null : matchById.get(f.getMatchId());
            int[] legsWon = null;
            if (m != null && m.getStatus() == Match.Status.COMPLETED) {
                legsWon = new int[]{legs.getOrDefault(m.getId() + ":" + f.getPlayer1Id(), 0),
                        legs.getOrDefault(m.getId() + ":" + f.getPlayer2Id(), 0)};
            }
            Integer winnerSlot = f.getWinnerId() == null ? null : (f.getWinnerId().equals(f.getPlayer1Id()) ? 1 : 2);
            return new TournamentViews.FixtureView(f.getId(), f.getRound(), f.getPosition(),
                    f.getPlayer1Id() == null ? null : players.get(f.getPlayer1Id()),
                    f.getPlayer2Id() == null ? null : players.get(f.getPlayer2Id()),
                    f.getMatchId(), m == null ? null : m.getStatus().name(), winnerSlot, f.getStatus().name(), legsWon);
        }).toList();

        int rounds = all.stream().mapToInt(TournamentFixture::getRound).max().orElse(0);
        List<TournamentViews.StandingRow> table = t.getFormat() == Tournament.Format.ROUND_ROBIN
                ? standings(t, all, players) : List.of();

        return new TournamentViews.TournamentDetail(summary(t, userById, entrants.size()), t.getCreatedBy(),
                entrants.stream().map(p -> new TournamentViews.Participant(players.get(p.userId()), p.getSeed())).toList(),
                fixtureViews, table, rounds);
    }

    private Map<Long, PlayerSummary> playerMap(UUID tournamentId) {
        List<Long> ids = participants.findByTournament(tournamentId).stream().map(TournamentParticipant::userId).toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, PlayerSummary::of));
    }

    private Map<String, Integer> legsByMatchAndUser(Set<UUID> matchIds) {
        Map<String, Integer> legs = new HashMap<>();
        if (matchIds.isEmpty()) return legs;
        for (MatchRecords.PlayerMatchStats s : stats.findAllByIdMatchIdIn(matchIds)) {
            legs.put(s.getId().getMatchId() + ":" + s.getId().getUserId(), s.getLegsWon());
        }
        return legs;
    }

    /** Round-robin table: points (2 per win), then leg difference, legs won, head-to-head, rating. */
    List<TournamentViews.StandingRow> standings(Tournament t, List<TournamentFixture> all, Map<Long, PlayerSummary> players) {
        Map<UUID, Match> matchById = matches.findAllById(all.stream().map(TournamentFixture::getMatchId)
                .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Match::getId, Function.identity()));
        Map<String, Integer> legs = legsByMatchAndUser(matchById.keySet());

        Map<Long, int[]> acc = new HashMap<>(); // played, won, lost, legsFor, legsAgainst
        players.keySet().forEach(id -> acc.put(id, new int[5]));
        Map<String, Long> headToHead = new HashMap<>();
        for (TournamentFixture f : all) {
            if (f.getStatus() != TournamentFixture.Status.COMPLETED || f.getMatchId() == null) continue;
            Match m = matchById.get(f.getMatchId());
            if (m == null || m.getStatus() != Match.Status.COMPLETED) continue;
            Long a = f.getPlayer1Id();
            Long b = f.getPlayer2Id();
            int la = legs.getOrDefault(m.getId() + ":" + a, 0);
            int lb = legs.getOrDefault(m.getId() + ":" + b, 0);
            int[] ra = acc.computeIfAbsent(a, k -> new int[5]);
            int[] rb = acc.computeIfAbsent(b, k -> new int[5]);
            ra[0]++; rb[0]++;
            ra[3] += la; ra[4] += lb;
            rb[3] += lb; rb[4] += la;
            if (m.getWinnerId().equals(a)) { ra[1]++; rb[2]++; } else { rb[1]++; ra[2]++; }
            headToHead.put(Math.min(a, b) + ":" + Math.max(a, b), m.getWinnerId());
        }
        List<TournamentViews.StandingRow> rows = new ArrayList<>();
        acc.forEach((id, r) -> rows.add(new TournamentViews.StandingRow(players.get(id), r[0], r[1], r[2], r[3], r[4],
                r[3] - r[4], r[1] * 2)));
        rows.sort((x, y) -> {
            int c = Integer.compare(y.points(), x.points());
            if (c != 0) return c;
            c = Integer.compare(y.legDiff(), x.legDiff());
            if (c != 0) return c;
            c = Integer.compare(y.legsFor(), x.legsFor());
            if (c != 0) return c;
            Long a = x.player().id();
            Long b = y.player().id();
            Long h2h = headToHead.get(Math.min(a, b) + ":" + Math.max(a, b));
            if (h2h != null) return h2h.equals(a) ? -1 : 1;
            return Integer.compare(y.player().rating(), x.player().rating());
        });
        return rows;
    }

    private TournamentViews.TournamentSummary summary(Tournament t, Map<Long, User> userById, int count) {
        User organiser = userById.get(t.getCreatedBy());
        User winner = t.getWinnerId() == null ? null : userById.get(t.getWinnerId());
        return new TournamentViews.TournamentSummary(t.getId(), t.getName(), t.getFormat().name(), t.getStatus().name(),
                MatchViews.ConfigView.of(t.config()), t.isRated(), count,
                organiser == null ? null : PlayerSummary.of(organiser),
                winner == null ? null : PlayerSummary.of(winner),
                t.getCreatedAt(), t.getStartedAt(), t.getCompletedAt());
    }
}
