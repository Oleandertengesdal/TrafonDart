package no.trafondart.history;

import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;
import no.trafondart.match.DartRepository;
import no.trafondart.match.LegRecordRepository;
import no.trafondart.match.Match;
import no.trafondart.match.MatchRecords.LegRecord;
import no.trafondart.match.MatchRecords.PlayerMatchStats;
import no.trafondart.match.MatchRepository;
import no.trafondart.match.PlayerMatchStatsRepository;
import no.trafondart.match.ThrownDart;
import no.trafondart.party.GameResultRepository;
import no.trafondart.party.GameType;
import no.trafondart.rating.RatingHistory;
import no.trafondart.rating.RatingHistoryRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads everything about one player's finished matches and games, for achievements and the year recap. */
@Service
public class PlayerHistoryService {

    private final MatchRepository matches;
    private final PlayerMatchStatsRepository stats;
    private final LegRecordRepository legs;
    private final DartRepository darts;
    private final RatingHistoryRepository ratings;
    private final GameResultRepository gameResults;
    private final UserRepository users;

    public PlayerHistoryService(MatchRepository matches, PlayerMatchStatsRepository stats, LegRecordRepository legs, DartRepository darts,
                                RatingHistoryRepository ratings, GameResultRepository gameResults, UserRepository users) {
        this.matches = matches;
        this.stats = stats;
        this.legs = legs;
        this.darts = darts;
        this.ratings = ratings;
        this.gameResults = gameResults;
        this.users = users;
    }

    /**
     * @param countedOnly only matches that are rated or were set up by one of the two players – what achievements use,
     *                    so nobody can invent matches for someone else to unlock things with
     */
    @Transactional(readOnly = true)
    public PlayerHistory load(User user, Instant from, Instant to, boolean countedOnly) {
        Long me = user.getId();
        List<Match> list = matches.findCompletedFor(me).stream()
                .filter(m -> m.getCompletedAt() != null && !m.getCompletedAt().isBefore(from) && m.getCompletedAt().isBefore(to))
                .filter(m -> !countedOnly || m.isRated() || m.getCreatedBy().equals(m.getPlayer1Id()) || m.getCreatedBy().equals(m.getPlayer2Id()))
                .sorted(Comparator.comparing(Match::getCompletedAt))
                .toList();
        List<UUID> ids = list.stream().map(Match::getId).toList();

        Map<String, PlayerMatchStats> statsByKey = new HashMap<>();
        Map<UUID, List<LegRecord>> legsByMatch = Map.of();
        Map<UUID, List<ThrownDart>> dartsByMatch = Map.of();
        Map<String, Double> ratingBefore = new HashMap<>();
        if (!ids.isEmpty()) {
            for (PlayerMatchStats s : stats.findAllByIdMatchIdIn(ids)) statsByKey.put(s.getId().getMatchId() + ":" + s.getId().getUserId(), s);
            legsByMatch = legs.findAllByMatchIdIn(ids).stream().collect(Collectors.groupingBy(LegRecord::getMatchId));
            dartsByMatch = darts.findAllByMatchIdInOrderByMatchIdAscSeqAsc(ids).stream().collect(Collectors.groupingBy(ThrownDart::getMatchId));
            for (RatingHistory r : ratings.findAllByMatchIdIn(ids)) ratingBefore.put(r.getMatchId() + ":" + r.getUserId(), r.getRatingBefore());
        }
        Set<Long> opponentIds = list.stream().map(m -> m.getPlayer1Id().equals(me) ? m.getPlayer2Id() : m.getPlayer1Id()).collect(Collectors.toSet());
        Map<Long, User> opponents = users.findAllById(opponentIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<PlayerHistory.MatchEntry> entries = new ArrayList<>();
        for (Match m : list) {
            int side = m.getPlayer1Id().equals(me) ? 0 : 1;
            Long oppId = side == 0 ? m.getPlayer2Id() : m.getPlayer1Id();
            List<Dart> d = dartsByMatch.getOrDefault(m.getId(), List.of()).stream().map(ThrownDart::toDart).toList();
            X01Engine.MatchState replay = X01Engine.replay(m.config(), m.getStartingPlayer(), d);
            entries.add(new PlayerHistory.MatchEntry(m, side, opponents.get(oppId), me.equals(m.getWinnerId()),
                    statsByKey.get(m.getId() + ":" + me), legsByMatch.getOrDefault(m.getId(), List.of()), replay,
                    ratingBefore.get(m.getId() + ":" + me), ratingBefore.get(m.getId() + ":" + oppId)));
        }

        List<PlayerHistory.GameEntry> games = new ArrayList<>();
        for (Object[] row : gameResults.findEntriesForUser(me)) {
            Instant at = (Instant) row[6];
            if (at == null || at.isBefore(from) || !at.isBefore(to)) continue;
            games.add(new PlayerHistory.GameEntry((UUID) row[0], (GameType) row[1], (Boolean) row[2], ((Number) row[3]).intValue(),
                    ((Number) row[4]).intValue(), ((Number) row[5]).intValue(), at));
        }
        return new PlayerHistory(user, entries, games);
    }
}
