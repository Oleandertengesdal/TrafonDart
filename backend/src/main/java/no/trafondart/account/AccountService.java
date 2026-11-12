package no.trafondart.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.trafondart.auth.PasswordResetTokenRepository;
import no.trafondart.awards.AwardEntities;
import no.trafondart.awards.MonthlyAwardRepository;
import no.trafondart.awards.YearEntities;
import no.trafondart.awards.YearlyAwardRepository;
import no.trafondart.challenge.Challenge;
import no.trafondart.challenge.ChallengeRepository;
import no.trafondart.challenge.ChallengeService;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.common.RateLimiter;
import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;
import no.trafondart.history.AchievementService;
import no.trafondart.match.DartRepository;
import no.trafondart.match.Match;
import no.trafondart.match.MatchRecords.PlayerMatchStats;
import no.trafondart.match.MatchRepository;
import no.trafondart.match.PlayerMatchStatsRepository;
import no.trafondart.match.ThrownDart;
import no.trafondart.party.GameResultRepository;
import no.trafondart.party.GameType;
import no.trafondart.rating.RatingHistoryRepository;
import no.trafondart.social.CommentLikeRepository;
import no.trafondart.social.CommentRepository;
import no.trafondart.social.KudosRepository;
import no.trafondart.social.SocialEntities;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A member's own data (see docs/PRIVACY.md): download everything stored about you, or delete your account.
 *
 * <p>Deleting keeps the matches – they are your opponents' history too – but the account loses its name for good:
 * it shows as "Deleted player", can never log in again, and your comments, kudos and likes are removed.
 */
@Service
public class AccountService {

    public static final RateLimiter.Rule EXPORT_PER_USER = new RateLimiter.Rule("export-user", 5, java.time.Duration.ofHours(1));
    private static final int MAX_PASSWORD_BYTES = 72;

    public record DeleteAccountRequest(@NotBlank @Size(max = 200) String password) {
    }

    // ---------------------------------------------------------------------------------------- export format
    public record Export(String format, Instant exportedAt, Profile profile, List<MatchRow> matches, List<GameRow> games,
                         List<CommentRow> comments, List<KudosRow> kudosGiven, List<LikeRow> commentLikes,
                         List<RatingRow> ratingHistory, List<AwardRow> awards, List<AchievementService.Achievement> achievements,
                         List<ChallengeRow> challenges) {
    }

    public record Profile(Long id, String username, String realName, String role, String preferredLocale, Instant memberSince,
                          double rating, double ratingDeviation, Instant lastRatedAt) {
    }

    public record MatchRow(UUID id, String status, boolean rated, Instant createdAt, Instant completedAt, String opponent,
                           Boolean won, int startScore, String inRule, String outRule, int legsToWin, Stats myStats,
                           List<String> myDarts) {
    }

    public record Stats(int legsWon, int dartsThrown, int pointsScored, int count180, int count140, int count100, int highestTurn,
                        int highestCheckout, int checkoutAttempts, int checkoutsHit) {
        static Stats of(PlayerMatchStats s) {
            return s == null ? null : new Stats(s.getLegsWon(), s.getDartsThrown(), s.getPointsScored(), s.getCount180(), s.getCount140(),
                    s.getCount100(), s.getHighestTurn(), s.getHighestCheckout(), s.getCheckoutAttempts(), s.getCheckoutsHit());
        }
    }

    public record GameRow(UUID id, GameType type, Instant completedAt, boolean won, int placement, int darts, int score) {
    }

    public record CommentRow(Long id, UUID matchId, String body, boolean deleted, Instant createdAt, Instant editedAt) {
    }

    public record KudosRow(UUID matchId, Instant givenAt) {
    }

    public record LikeRow(Long commentId, Instant likedAt) {
    }

    public record RatingRow(UUID matchId, Instant ratedAt, double ratingBefore, double ratingAfter) {
    }

    public record AwardRow(String period, String award, double value) {
    }

    public record ChallengeRow(Long id, String role, String otherPlayer, String status, Instant createdAt, UUID matchId, String message) {
    }

    private final UserRepository users;
    private final MatchRepository matches;
    private final PlayerMatchStatsRepository stats;
    private final DartRepository darts;
    private final GameResultRepository gameResults;
    private final CommentRepository comments;
    private final KudosRepository kudos;
    private final CommentLikeRepository likes;
    private final RatingHistoryRepository ratings;
    private final MonthlyAwardRepository monthly;
    private final YearlyAwardRepository yearly;
    private final AchievementService achievements;
    private final ChallengeRepository challengeRepo;
    private final ChallengeService challenges;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository users, MatchRepository matches, PlayerMatchStatsRepository stats, DartRepository darts,
                          GameResultRepository gameResults, CommentRepository comments, KudosRepository kudos, CommentLikeRepository likes,
                          RatingHistoryRepository ratings, MonthlyAwardRepository monthly, YearlyAwardRepository yearly,
                          AchievementService achievements, ChallengeRepository challengeRepo, ChallengeService challenges,
                          PasswordResetTokenRepository resetTokens, PasswordEncoder encoder, RateLimiter rateLimiter) {
        this.users = users;
        this.matches = matches;
        this.stats = stats;
        this.darts = darts;
        this.gameResults = gameResults;
        this.comments = comments;
        this.kudos = kudos;
        this.likes = likes;
        this.ratings = ratings;
        this.monthly = monthly;
        this.yearly = yearly;
        this.achievements = achievements;
        this.challengeRepo = challengeRepo;
        this.challenges = challenges;
        this.resetTokens = resetTokens;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public Export export(Long userId) {
        rateLimiter.check(EXPORT_PER_USER, String.valueOf(userId));
        User me = users.findById(userId).orElseThrow(ApiException::notFound);
        List<Match> mine = matches.findAllForPlayer(userId);
        List<UUID> ids = mine.stream().map(Match::getId).toList();
        Map<String, PlayerMatchStats> statsByMatch = new HashMap<>();
        Map<UUID, List<ThrownDart>> dartsByMatch = Map.of();
        if (!ids.isEmpty()) {
            stats.findAllByIdMatchIdIn(ids).stream().filter(s -> s.getId().getUserId().equals(userId))
                    .forEach(s -> statsByMatch.put(s.getId().getMatchId().toString(), s));
            dartsByMatch = darts.findAllByMatchIdInOrderByMatchIdAscSeqAsc(ids).stream().collect(Collectors.groupingBy(ThrownDart::getMatchId));
        }
        Map<Long, User> opponents = users.findAllById(mine.stream().map(m -> m.getPlayer1Id().equals(userId) ? m.getPlayer2Id() : m.getPlayer1Id())
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<MatchRow> matchRows = new ArrayList<>();
        for (Match m : mine) {
            int side = m.getPlayer1Id().equals(userId) ? 0 : 1;
            User opp = opponents.get(side == 0 ? m.getPlayer2Id() : m.getPlayer1Id());
            List<Dart> all = dartsByMatch.getOrDefault(m.getId(), List.of()).stream().map(ThrownDart::toDart).toList();
            List<String> myDarts = new ArrayList<>();
            X01Engine.MatchState replay = X01Engine.replay(m.config(), m.getStartingPlayer(), all);
            for (X01Engine.Leg leg : replay.legs()) {
                for (X01Engine.Turn t : leg.turns()) {
                    if (t.playerIndex() == side) t.darts().forEach(d -> myDarts.add(d.dart().label()));
                }
            }
            Boolean won = m.getWinnerId() == null ? null : m.getWinnerId().equals(userId);
            matchRows.add(new MatchRow(m.getId(), m.getStatus().name(), m.isRated(), m.getCreatedAt(), m.getCompletedAt(),
                    opp == null ? null : opp.getUsername(), won, m.config().startScore(), m.config().inRule().name(),
                    m.config().outRule().name(), m.config().legsToWin(), Stats.of(statsByMatch.get(m.getId().toString())), myDarts));
        }

        List<GameRow> gameRows = gameResults.findEntriesForUser(userId).stream()
                .map(r -> new GameRow((UUID) r[0], (GameType) r[1], (Instant) r[6], (Boolean) r[2], ((Number) r[3]).intValue(),
                        ((Number) r[4]).intValue(), ((Number) r[5]).intValue()))
                .toList();
        List<CommentRow> commentRows = comments.findByAuthor(userId).stream()
                .map(c -> new CommentRow(c.getId(), c.getMatchId(), c.getBody(), c.isDeleted(), c.getCreatedAt(), c.getEditedAt())).toList();
        List<KudosRow> kudosRows = kudos.findByUser(userId).stream().map(k -> new KudosRow(k.getId().getMatchId(), k.getCreatedAt())).toList();
        List<LikeRow> likeRows = likes.findByUser(userId).stream().map(l -> new LikeRow(l.getId().getCommentId(), l.getCreatedAt())).toList();
        List<RatingRow> ratingRows = ratings.findAllByUserIdOrderByRatedAtAscIdAsc(userId).stream()
                .map(r -> new RatingRow(r.getMatchId(), r.getRatedAt(), r.getRatingBefore(), r.getRatingAfter())).toList();
        List<AwardRow> awardRows = new ArrayList<>();
        for (AwardEntities.MonthlyAward a : monthly.findByUser(userId)) awardRows.add(new AwardRow(a.getPeriod(), a.getAward().name(), a.getValue()));
        for (YearEntities.YearlyAward a : yearly.findByUser(userId)) awardRows.add(new AwardRow(String.valueOf(a.getYear()), a.getAward().name(), a.getValue()));
        List<AchievementService.Achievement> unlocked = achievements.cabinet(me).items().stream().filter(AchievementService.Achievement::unlocked).toList();

        List<Challenge> chs = challengeRepo.findAllFor(userId);
        Map<Long, User> others = users.findAllById(chs.stream().map(c -> c.getChallengerId().equals(userId) ? c.getOpponentId() : c.getChallengerId())
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<ChallengeRow> challengeRows = chs.stream().map(c -> {
            boolean sent = c.getChallengerId().equals(userId);
            User other = others.get(sent ? c.getOpponentId() : c.getChallengerId());
            return new ChallengeRow(c.getId(), sent ? "SENT" : "RECEIVED", other == null ? null : other.getUsername(), c.getStatus().name(),
                    c.getCreatedAt(), c.getMatchId(), c.getMessage());
        }).toList();

        Profile profile = new Profile(me.getId(), me.getUsername(), me.getRealName(), me.getRole().name(), me.getPreferredLocale(),
                me.getCreatedAt(), me.getRating(), me.getRatingDeviation(), me.getLastRatedAt());
        return new Export("trafondart-export/1", Instant.now(), profile, matchRows, gameRows, commentRows, kudosRows, likeRows,
                ratingRows, awardRows, unlocked, challengeRows);
    }

    @Transactional
    public void delete(Long userId, DeleteAccountRequest req) {
        rateLimiter.check(RateLimiter.PASSWORD_CHANGE_PER_USER, String.valueOf(userId));
        User user = users.findAllByIdForUpdate(List.of(userId)).stream().findFirst().orElseThrow(ApiException::notFound);
        if (user.isDeleted()) throw ApiException.notFound();
        if (req.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_INCORRECT);
        }
        if (user.isAdmin() && user.isEnabled() && users.countByRoleAndEnabledTrue(User.Role.ADMIN) <= 1) {
            throw new ApiException(ErrorCode.LAST_ADMIN);
        }
        Instant now = Instant.now();
        for (SocialEntities.Comment c : comments.findByAuthor(userId)) {
            if (!c.isDeleted()) c.markDeleted();
        }
        kudos.deleteByUser(userId);
        likes.deleteByUser(userId);
        challenges.cancelAllFor(userId);
        resetTokens.invalidateOpenFor(userId, now);
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        user.markDeleted(encoder.encode(Base64.getEncoder().encodeToString(secret)), now);
    }
}
