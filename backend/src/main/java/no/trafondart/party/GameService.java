package no.trafondart.party;

import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.game.Dart;
import no.trafondart.match.MatchEvents;
import no.trafondart.party.GameEntities.Game;
import no.trafondart.party.GameEntities.GameDart;
import no.trafondart.party.GameEntities.GamePlayer;
import no.trafondart.party.GameEntities.GameResult;
import no.trafondart.party.GameEntities.Status;
import no.trafondart.party.GameViews.CreateGameRequest;
import no.trafondart.party.GameViews.GameStats;
import no.trafondart.party.GameViews.GameSummary;
import no.trafondart.party.GameViews.GameView;
import no.trafondart.party.GameViews.PlayerSlot;
import no.trafondart.party.GameViews.ThrowRequest;
import no.trafondart.party.GameViews.TypeStats;
import no.trafondart.party.GameViews.UndoRequest;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Party games: set-up, scoring (one dart at a time, with the same optimistic "expected dart count" as matches),
 * undo, abandon, results and statistics. None of this touches ratings or the monthly awards.
 */
@Service
@Transactional
public class GameService {

    /** Published after a game is completed, for achievements. */
    public record GameCompleted(UUID gameId, List<Long> userIds) {
    }

    /** The full scoreboard, sent to /topic/games/{id} after commit. */
    public record GameUpdated(GameView view) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GameRepository games;
    private final GamePlayerRepository players;
    private final GameDartRepository darts;
    private final GameResultRepository results;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;

    public GameService(GameRepository games, GamePlayerRepository players, GameDartRepository darts, GameResultRepository results,
                       UserRepository users, ApplicationEventPublisher events, JsonMapper json) {
        this.games = games;
        this.players = players;
        this.darts = darts;
        this.results = results;
        this.users = users;
        this.events = events;
        this.json = json;
    }

    // ------------------------------------------------------------------ set-up

    public GameView create(AuthUser actor, CreateGameRequest req) {
        List<Long> ids = req.playerIds();
        if (new HashSet<>(ids).size() != ids.size()) throw new ApiException(ErrorCode.MATCH_PLAYERS_SAME);
        if (!ids.contains(actor.id()) && !actor.isAdmin()) throw ApiException.forbidden(); // no setting up games for others
        Map<Long, User> found = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        for (Long id : ids) {
            User u = found.get(id);
            if (u == null || !u.isEnabled()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }

        // Doubles: [a1, a2, b1, b2] → throwing order A1, B1, A2, B2.
        List<Long> order = req.teams() && ids.size() == 4 ? List.of(ids.get(0), ids.get(2), ids.get(1), ids.get(3)) : ids;
        PartySettings requested = req.settings();
        if (req.type() == GameType.KILLER) {
            List<Integer> pool = new ArrayList<>();
            for (int i = 1; i <= 20; i++) pool.add(i);
            Collections.shuffle(pool, RANDOM);
            requested = new PartySettings(null, null, null, null, requested == null ? null : requested.lives(), null, null,
                    pool.subList(0, Math.min(order.size(), 20)));
        }
        PartySettings settings = PartyRules.normalise(req.type(), req.teams(), order.size(), requested);

        Game game = games.save(new Game(req.type(), req.teams(), json.writeValueAsString(settings), actor.id()));
        for (int slot = 0; slot < order.size(); slot++) {
            players.save(new GamePlayer(game.getId(), slot, order.get(slot), req.teams() ? slot % 2 : slot));
        }
        players.flush();
        GameView view = view(game, null);
        publish(view);
        return view;
    }

    // ------------------------------------------------------------------ scoring

    public GameView throwDart(AuthUser actor, UUID id, ThrowRequest req) {
        if (!Dart.isValid(req.segment(), req.multiplier())) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        Game game = games.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        List<GamePlayer> lineup = players.findByGame(id);
        requireScorer(actor, game, lineup);
        if (game.getStatus() != Status.IN_PROGRESS) throw new ApiException(ErrorCode.MATCH_NOT_IN_PROGRESS);
        List<GameDart> stored = darts.findByGame(id);
        if (req.expectedDartCount() != stored.size()) {
            throw new ApiException(ErrorCode.MATCH_OUT_OF_SYNC, view(game, lineup, stored));
        }
        GameDart dart = darts.save(new GameDart(id, stored.size() + 1, req.segment(), req.multiplier()));
        List<GameDart> all = new ArrayList<>(stored);
        all.add(dart);
        PartyState state = replay(game, lineup, all);
        if (state.finished()) complete(game, lineup, state);
        GameView view = view(game, lineup, all, state);
        publish(view);
        return view;
    }

    public GameView undo(AuthUser actor, UUID id, UndoRequest req) {
        Game game = games.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        List<GamePlayer> lineup = players.findByGame(id);
        requireScorer(actor, game, lineup);
        if (game.getStatus() != Status.IN_PROGRESS) throw new ApiException(ErrorCode.MATCH_NOT_IN_PROGRESS);
        List<GameDart> stored = darts.findByGame(id);
        if (stored.isEmpty()) throw new ApiException(ErrorCode.MATCH_NOTHING_TO_UNDO);
        if (req.expectedDartCount() != stored.size()) throw new ApiException(ErrorCode.MATCH_OUT_OF_SYNC, view(game, lineup, stored));
        darts.deleteDart(id, stored.size());
        List<GameDart> left = stored.subList(0, stored.size() - 1);
        GameView view = view(game, lineup, left);
        publish(view);
        return view;
    }

    public GameView abandon(AuthUser actor, UUID id) {
        Game game = games.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        List<GamePlayer> lineup = players.findByGame(id);
        requireScorer(actor, game, lineup);
        if (game.getStatus() != Status.IN_PROGRESS) throw new ApiException(ErrorCode.MATCH_NOT_IN_PROGRESS);
        game.setStatus(Status.ABANDONED);
        game.setCompletedAt(Instant.now());
        GameView view = view(game, lineup);
        publish(view);
        return view;
    }

    private void complete(Game game, List<GamePlayer> lineup, PartyState state) {
        game.setStatus(Status.COMPLETED);
        game.setWinnerSide(state.winnerSide());
        game.setCompletedAt(Instant.now());
        for (GamePlayer p : lineup) {
            PartyState.SlotStats s = state.slots().get(p.getSlot());
            results.save(new GameResult(game.getId(), p.getUserId(), s.placement(), s.darts(), s.score(), p.getSide() == state.winnerSide()));
        }
        events.publishEvent(new GameCompleted(game.getId(), lineup.stream().map(GamePlayer::getUserId).toList()));
    }

    private static void requireScorer(AuthUser actor, Game game, List<GamePlayer> lineup) {
        boolean ok = actor.isAdmin() || game.getCreatedBy().equals(actor.id())
                || lineup.stream().anyMatch(p -> p.getUserId().equals(actor.id()));
        if (!ok) throw new ApiException(ErrorCode.MATCH_NOT_SCORER);
    }

    private void publish(GameView view) {
        events.publishEvent(new GameUpdated(view));
        events.publishEvent(new MatchEvents.FeedChanged(view.id()));
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public GameView get(UUID id) {
        return view(games.findById(id).orElseThrow(ApiException::notFound), null);
    }

    @Transactional(readOnly = true)
    public List<GameSummary> live() {
        return summaries(games.findLive());
    }

    @Transactional(readOnly = true)
    public List<GameSummary> recent(String username, int limit) {
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, 100));
        if (username == null || username.isBlank()) return summaries(games.findRecent(page));
        User u = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        return summaries(games.findRecentFor(u.getId(), page));
    }

    @Transactional(readOnly = true)
    public GameStats stats(String username) {
        User u = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        Map<GameType, int[]> totals = new EnumMap<>(GameType.class); // played, won, darts, score
        Map<GameType, Double> best = new EnumMap<>(GameType.class);
        for (Object[] row : results.findForUser(u.getId())) {
            GameType type = (GameType) row[0];
            boolean won = (Boolean) row[1];
            int dartsThrown = ((Number) row[3]).intValue();
            int score = ((Number) row[4]).intValue();
            int[] t = totals.computeIfAbsent(type, k -> new int[4]);
            t[0]++;
            if (won) t[1]++;
            t[2] += dartsThrown;
            t[3] += score;
            Double candidate = switch (type) {
                case X01, CRICKET -> dartsThrown == 0 ? null : score * 3.0 / dartsThrown;
                case KILLER -> (double) score;
                case AROUND_THE_CLOCK -> won ? (double) dartsThrown : null;
            };
            if (candidate != null) {
                Double current = best.get(type);
                boolean lowerIsBetter = type == GameType.AROUND_THE_CLOCK;
                if (current == null || (lowerIsBetter ? candidate < current : candidate > current)) best.put(type, candidate);
            }
        }
        List<TypeStats> types = new ArrayList<>();
        for (GameType type : GameType.values()) {
            int[] t = totals.getOrDefault(type, new int[4]);
            Double average = switch (type) {
                case X01, CRICKET -> t[2] == 0 ? null : round1(t[3] * 3.0 / t[2]);
                case KILLER -> t[0] == 0 ? null : round1((double) t[3] / t[0]);
                case AROUND_THE_CLOCK -> null;
            };
            Double b = best.get(type);
            types.add(new TypeStats(type, t[0], t[1], t[2], average, b == null ? null : round1(b)));
        }
        return new GameStats(PlayerSummary.of(u), types);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    // ------------------------------------------------------------------ helpers

    PartySettings settings(Game game) {
        return json.readValue(game.getSettings(), PartySettings.class);
    }

    PartyState replay(Game game, List<GamePlayer> lineup, List<GameDart> stored) {
        List<Integer> sides = lineup.stream().map(GamePlayer::getSide).toList();
        List<Dart> d = stored.stream().map(x -> new Dart(x.getSegment(), x.getMultiplier())).toList();
        return PartyEngine.replay(game.getType(), settings(game), sides, d);
    }

    private GameView view(Game game, List<GamePlayer> lineup) {
        List<GamePlayer> l = lineup == null ? players.findByGame(game.getId()) : lineup;
        return view(game, l, darts.findByGame(game.getId()));
    }

    private GameView view(Game game, List<GamePlayer> lineup, List<GameDart> stored) {
        return view(game, lineup, stored, replay(game, lineup, stored));
    }

    private GameView view(Game game, List<GamePlayer> lineup, List<GameDart> stored, PartyState st) {
        Map<Long, PlayerSummary> who = people(lineup.stream().map(GamePlayer::getUserId).toList());
        List<PlayerSlot> slots = lineup.stream().map(p -> new PlayerSlot(p.getSlot(), p.getSide(), who.get(p.getUserId()))).toList();
        return new GameView(game.getId(), game.getType(), game.isTeamPlay(), false, game.getStatus().name(), settings(game), slots,
                st.sides(), st.slots(), st.currentSlot(), st.leg(), st.round(),
                st.turnDarts().stream().map(Dart::label).toList(), st.checkout().stream().map(Dart::label).toList(),
                st.lastDart() == null ? null : st.lastDart().label(),
                game.getStatus() == Status.COMPLETED ? game.getWinnerSide() : null, stored.size(),
                game.getCreatedBy(), game.getCreatedAt(), game.getCompletedAt());
    }

    private Map<Long, PlayerSummary> people(List<Long> ids) {
        Map<Long, PlayerSummary> out = new HashMap<>();
        for (User u : users.findAllById(ids)) out.put(u.getId(), PlayerSummary.of(u));
        return out;
    }

    private List<GameSummary> summaries(List<Game> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<GamePlayer>> byGame = players.findByGames(list.stream().map(Game::getId).toList()).stream()
                .collect(Collectors.groupingBy(GamePlayer::getGameId));
        Set<Long> userIds = byGame.values().stream().flatMap(List::stream).map(GamePlayer::getUserId).collect(Collectors.toSet());
        Map<Long, PlayerSummary> who = people(new ArrayList<>(userIds));
        return list.stream().map(g -> new GameSummary(g.getId(), g.getType(), g.isTeamPlay(), g.getStatus().name(),
                byGame.getOrDefault(g.getId(), List.of()).stream().map(p -> new PlayerSlot(p.getSlot(), p.getSide(), who.get(p.getUserId()))).toList(),
                g.getStatus() == Status.COMPLETED ? g.getWinnerSide() : null, g.getCreatedAt(), g.getCompletedAt())).toList();
    }
}
