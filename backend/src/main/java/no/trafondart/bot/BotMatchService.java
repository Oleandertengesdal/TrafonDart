package no.trafondart.bot;

import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.game.CheckoutCalculator;
import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig;
import no.trafondart.game.X01Engine;
import no.trafondart.match.MatchViewMapper;
import no.trafondart.match.MatchViews;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static no.trafondart.bot.BotMatch.BOT;
import static no.trafondart.bot.BotMatch.PLAYER;
import static no.trafondart.match.MatchViewMapper.round2;

/**
 * Practice matches against Trafo. You score your own darts as in any match; whenever the turn passes to Trafo, it
 * throws its turn in the same request. Nothing here touches matches, ratings or the stats built from them.
 */
@Service
public class BotMatchService {

    /** How Trafo appears on the scoreboard. Not a member: id 0, never linked. */
    public static final String BOT_NAME = "Trafo";
    private static final int RECENT = 10;

    private final BotMatchRepository matches;
    private final BotDartRepository darts;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();

    public BotMatchService(BotMatchRepository matches, BotDartRepository darts, UserRepository users) {
        this.matches = matches;
        this.darts = darts;
        this.users = users;
    }

    // ------------------------------------------------------------------ play

    @Transactional
    public BotViews.BotMatchView create(AuthUser actor, BotViews.CreateBotMatchRequest req) {
        GameConfig config = new GameConfig(req.startScore(), req.inRule(), req.outRule(), req.legsToWin());
        matches.findFirstByUserIdAndStatus(actor.id(), BotMatch.Status.IN_PROGRESS).ifPresent(open -> {
            throw new ApiException(ErrorCode.BOT_MATCH_IN_PROGRESS, Map.of("id", open.getId()));
        });
        int starter = req.startingPlayer() != null ? req.startingPlayer() : random.nextInt(2);
        BotMatch match = matches.saveAndFlush(new BotMatch(actor.id(), req.level(), config, starter, random.nextLong()));
        List<Dart> sequence = new ArrayList<>();
        X01Engine.MatchState state = letTrafoThrow(match, sequence, X01Engine.replay(config, starter, sequence));
        return view(match, state);
    }

    @Transactional(readOnly = true)
    public BotViews.BotMatchView get(UUID id, AuthUser actor) {
        BotMatch match = matches.findById(id).filter(m -> m.getUserId().equals(actor.id())).orElseThrow(ApiException::notFound);
        return view(match, replay(match));
    }

    @Transactional
    public BotViews.BotMatchView throwDart(UUID id, AuthUser actor, MatchViews.ThrowRequest req) {
        BotMatch match = lockForPlay(id, actor);
        List<Dart> sequence = sequence(match);
        if (req.expectedDartCount() != sequence.size()) {
            throw new ApiException(ErrorCode.MATCH_OUT_OF_SYNC, view(match, replay(match, sequence)));
        }
        if (!Dart.isValid(req.segment(), req.multiplier())) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        X01Engine.MatchState state = replay(match, sequence);
        if (state.currentPlayerIndex() != PLAYER) {
            // Can't normally happen (Trafo always throws at once); catch up before taking the dart.
            state = letTrafoThrow(match, sequence, state);
            if (state.finished()) return view(match, state);
        }
        Dart dart = new Dart(req.segment(), req.multiplier());
        sequence.add(dart);
        darts.save(new BotDart(match.getId(), sequence.size(), dart));
        state = letTrafoThrow(match, sequence, replay(match, sequence));
        return view(match, state);
    }

    /** Takes back your last dart, and whatever Trafo threw after it. */
    @Transactional
    public BotViews.BotMatchView undo(UUID id, AuthUser actor, MatchViews.UndoRequest req) {
        BotMatch match = lockForPlay(id, actor);
        List<Dart> sequence = sequence(match);
        X01Engine.MatchState state = replay(match, sequence);
        int[] thrower = throwers(state);
        int lastMine = -1;
        for (int i = 0; i < thrower.length; i++) if (thrower[i] == PLAYER) lastMine = i;
        if (lastMine < 0) throw new ApiException(ErrorCode.MATCH_NOTHING_TO_UNDO);
        if (req.expectedDartCount() != sequence.size()) throw new ApiException(ErrorCode.MATCH_OUT_OF_SYNC, view(match, state));
        darts.deleteAfter(match.getId(), lastMine);
        darts.flush();
        return view(match, replay(match, sequence.subList(0, lastMine)));
    }

    /**
     * Giving up. Once you have thrown, it counts as a loss (so a losing match can't be dropped to keep a streak);
     * before that, the match is simply removed. Returns null when it was removed.
     */
    @Transactional
    public BotViews.BotMatchView forfeit(UUID id, AuthUser actor) {
        BotMatch match = lockForPlay(id, actor);
        X01Engine.MatchState state = replay(match);
        boolean thrown = false;
        for (int t : throwers(state)) thrown |= t == PLAYER;
        if (!thrown) {
            matches.delete(match);
            return null;
        }
        match.finish(BotMatch.Status.FORFEITED, false, state, Instant.now());
        return view(match, state);
    }

    /** Trafo throws for as long as it's its turn (one turn, or more if it wins a leg and starts the next). */
    private X01Engine.MatchState letTrafoThrow(BotMatch match, List<Dart> sequence, X01Engine.MatchState state) {
        while (!state.finished() && state.currentPlayerIndex() == BOT) {
            Dart dart = BotPlayer.nextDart(state, BOT, match.getLevel(), match.getSeed());
            sequence.add(dart);
            darts.save(new BotDart(match.getId(), sequence.size(), dart));
            state = replay(match, sequence);
        }
        if (state.finished()) {
            match.finish(BotMatch.Status.COMPLETED, state.winnerIndex() == PLAYER, state, Instant.now());
        }
        return state;
    }

    private BotMatch lockForPlay(UUID id, AuthUser actor) {
        BotMatch match = matches.findByIdForUpdate(id).filter(m -> m.getUserId().equals(actor.id())).orElseThrow(ApiException::notFound);
        if (match.getStatus() != BotMatch.Status.IN_PROGRESS) throw new ApiException(ErrorCode.MATCH_NOT_IN_PROGRESS);
        return match;
    }

    private List<Dart> sequence(BotMatch match) {
        return new ArrayList<>(darts.findAllByMatchIdOrderBySeqAsc(match.getId()).stream().map(BotDart::toDart).toList());
    }

    private X01Engine.MatchState replay(BotMatch match) {
        return replay(match, sequence(match));
    }

    private static X01Engine.MatchState replay(BotMatch match, List<Dart> sequence) {
        return X01Engine.replay(match.config(), match.getStartingPlayer(), sequence);
    }

    /** Who threw each dart, in order. */
    static int[] throwers(X01Engine.MatchState state) {
        List<Integer> out = new ArrayList<>();
        for (X01Engine.Leg leg : state.legs()) {
            for (X01Engine.Turn turn : leg.turns()) {
                for (int i = 0; i < turn.darts().size(); i++) out.add(turn.playerIndex());
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ views

    static PlayerSummary trafo() {
        return new PlayerSummary(0L, "trafo", BOT_NAME, 0, 0, false, false);
    }

    private BotViews.BotMatchView view(BotMatch match, X01Engine.MatchState s) {
        User me = users.findById(match.getUserId()).orElseThrow();
        boolean inProgress = match.getStatus() == BotMatch.Status.IN_PROGRESS;
        List<String> checkout = null;
        if (inProgress && !s.finished() && s.opened()[s.currentPlayerIndex()]) {
            checkout = CheckoutCalculator.suggest(s.remaining()[s.currentPlayerIndex()], s.dartsLeftInTurn(), match.config().outRule())
                    .map(r -> r.stream().map(Dart::label).toList()).orElse(null);
        }
        List<MatchViews.LivePlayerStats> stats = new ArrayList<>();
        for (X01Engine.PlayerStats ps : s.stats()) {
            stats.add(new MatchViews.LivePlayerStats(round2(ps.threeDartAverage()), round2(ps.first9Average()),
                    ps.dartsThrown(), ps.pointsScored(), ps.count180(), ps.count140(), ps.count100(),
                    ps.highestTurn(), ps.highestCheckout(), ps.checkoutAttempts(), ps.checkoutsHit(), round2(ps.checkoutPercentage())));
        }
        List<MatchViews.LegView> legs = s.legs().stream()
                .map(l -> new MatchViews.LegView(l.legNo(), l.starterIndex(), l.winnerIndex(),
                        l.winnerIndex() == null ? 0 : l.dartsThrownBy(l.winnerIndex()), l.checkoutScore()))
                .toList();
        Integer winner = switch (match.getStatus()) {
            case IN_PROGRESS -> null;
            case COMPLETED -> s.winnerIndex();
            case FORFEITED -> BOT;
        };
        return new BotViews.BotMatchView(
                match.getId(),
                match.getStatus().name(),
                match.getLevel().name(),
                match.getLevel().typicalAverage(),
                MatchViews.ConfigView.of(match.config()),
                false,
                null,
                List.of(PlayerSummary.of(me), trafo()),
                List.of(me.getId()),
                s.currentLeg().legNo(),
                s.currentPlayerIndex(),
                s.legsWon(),
                s.remaining(),
                s.opened(),
                MatchViewMapper.turnView(s.currentTurn()),
                MatchViewMapper.turnView(s.lastCompletedTurn()),
                s.dartCount() > 0 && s.currentTurn() == null,
                stats,
                checkout,
                s.dartsLeftInTurn(),
                s.lastEvent().name(),
                winner,
                s.dartCount(),
                legs,
                lastPlayerTurn(s),
                botTurnsSinceLastPlayerDart(s),
                match.getCreatedAt(),
                match.getCompletedAt(),
                false);
    }

    private static List<X01Engine.Turn> turnsWithDarts(X01Engine.MatchState s) {
        List<X01Engine.Turn> turns = new ArrayList<>();
        for (X01Engine.Leg leg : s.legs()) {
            for (X01Engine.Turn t : leg.turns()) if (!t.darts().isEmpty()) turns.add(t);
        }
        return turns;
    }

    /** Your latest turn, finished or not (null before your first dart). */
    static MatchViews.TurnView lastPlayerTurn(X01Engine.MatchState s) {
        List<X01Engine.Turn> turns = turnsWithDarts(s);
        for (int i = turns.size() - 1; i >= 0; i--) {
            if (turns.get(i).playerIndex() == PLAYER) return MatchViewMapper.turnView(turns.get(i));
        }
        return null;
    }

    /** Trafo's turns after your latest dart: what it threw in answer, to be shown dart by dart. */
    static List<MatchViews.TurnView> botTurnsSinceLastPlayerDart(X01Engine.MatchState s) {
        List<X01Engine.Turn> turns = turnsWithDarts(s);
        int lastMine = -1;
        for (int i = 0; i < turns.size(); i++) if (turns.get(i).playerIndex() == PLAYER) lastMine = i;
        return turns.subList(lastMine + 1, turns.size()).stream().map(MatchViewMapper::turnView).toList();
    }

    // ------------------------------------------------------------------ the record

    @Transactional(readOnly = true)
    public BotViews.BotRecord record(String username, AuthUser viewer) {
        User user = users.findByUsernameIgnoreCase(username).filter(u -> !u.isDeleted()).orElseThrow(ApiException::notFound);
        List<BotMatch> finished = matches.findFinishedFor(user.getId());

        int won = 0, streak = 0, best = 0;
        BotLevel hardest = null;
        int[][] perLevel = new int[BotLevel.values().length][2];
        long darts = 0, points = 0, f9d = 0, f9p = 0, attempts = 0, hits = 0, trafoSum = 0;
        int c180 = 0, c140 = 0, c100 = 0, highestTurn = 0, highestCheckout = 0;
        double bestAverage = 0, trafoTotal = 0;
        for (BotMatch m : finished) {
            boolean w = Boolean.TRUE.equals(m.getWon());
            perLevel[m.getLevel().ordinal()][0]++;
            if (w) {
                won++;
                perLevel[m.getLevel().ordinal()][1]++;
                streak++;
                best = Math.max(best, streak);
                if (hardest == null || m.getLevel().compareTo(hardest) > 0) hardest = m.getLevel();
            } else {
                streak = 0;
            }
            darts += m.getDartsThrown();
            points += m.getPointsScored();
            f9d += m.getFirst9Darts();
            f9p += m.getFirst9Points();
            attempts += m.getCheckoutAttempts();
            hits += m.getCheckoutsHit();
            c180 += m.getCount180();
            c140 += m.getCount140();
            c100 += m.getCount100();
            highestTurn = Math.max(highestTurn, m.getHighestTurn());
            highestCheckout = Math.max(highestCheckout, m.getHighestCheckout());
            if (m.getStatus() == BotMatch.Status.COMPLETED) bestAverage = Math.max(bestAverage, m.average());
            if (m.getBotAverage() > 0) {
                trafoTotal += m.getBotAverage();
                trafoSum++;
            }
        }
        int played = finished.size();
        List<BotViews.LevelRecord> levels = new ArrayList<>();
        for (BotLevel l : BotLevel.values()) {
            levels.add(new BotViews.LevelRecord(l.name(), l.typicalAverage(), perLevel[l.ordinal()][0], perLevel[l.ordinal()][1]));
        }
        BotViews.Throwing throwing = new BotViews.Throwing(
                darts == 0 ? 0 : round2(points * 3.0 / darts),
                f9d == 0 ? 0 : round2(f9p * 3.0 / f9d),
                round2(bestAverage), c180, c140, c100, highestTurn, highestCheckout, (int) attempts, (int) hits,
                attempts == 0 ? 0 : round2(hits * 100.0 / attempts),
                trafoSum == 0 ? 0 : round2(trafoTotal / trafoSum));
        List<BotViews.RecentBotMatch> recent = new ArrayList<>();
        for (int i = finished.size() - 1; i >= 0 && recent.size() < RECENT; i--) {
            BotMatch m = finished.get(i);
            recent.add(new BotViews.RecentBotMatch(m.getId(), m.getLevel().name(), m.getStatus().name(), Boolean.TRUE.equals(m.getWon()),
                    m.getLegsWon(), m.getLegsLost(), round2(m.average()), round2(m.getBotAverage()), m.config().startScore(), m.getCompletedAt()));
        }
        UUID open = viewer != null && viewer.id().equals(user.getId())
                ? matches.findFirstByUserIdAndStatus(user.getId(), BotMatch.Status.IN_PROGRESS).map(BotMatch::getId).orElse(null)
                : null;
        return new BotViews.BotRecord(PlayerSummary.of(user), played, won, played - won,
                played == 0 ? 0 : round2(won * 100.0 / played), streak, best, hardest == null ? null : hardest.name(),
                levels, throwing, recent, open);
    }
}
