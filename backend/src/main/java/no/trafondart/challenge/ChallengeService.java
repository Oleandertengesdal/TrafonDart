package no.trafondart.challenge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.common.TextInput;
import no.trafondart.game.GameConfig;
import no.trafondart.match.Match;
import no.trafondart.match.MatchService;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Challenge a friend (see docs/USER-GUIDE.md): a pending challenge waits 48 hours for an answer; once accepted it
 * waits another 48 hours on both players' home pages until one of them presses Start, which creates the match.
 */
@Service
public class ChallengeService {

    public static final Duration ANSWER_WITHIN = Duration.ofHours(48);
    public static final Duration START_WITHIN = Duration.ofHours(48);
    public static final int MAX_OPEN_PER_PLAYER = 5;
    public static final int MAX_MESSAGE = 200;

    public record CreateChallengeRequest(@NotNull Long opponentId, @NotNull Integer startScore, @NotNull GameConfig.InRule inRule,
                                         @NotNull GameConfig.OutRule outRule,
                                         @NotNull @Min(1) @Max(GameConfig.MAX_LEGS_TO_WIN) Integer legsToWin, Boolean rated,
                                         String message) {
    }

    public record ChallengeView(Long id, PlayerSummary challenger, PlayerSummary opponent, int startScore, String inRule,
                                String outRule, int legsToWin, boolean rated, String message, String status, UUID matchId,
                                Instant createdAt, Instant expiresAt, Instant respondedAt) {
    }

    public record ChallengeList(List<ChallengeView> incoming, List<ChallengeView> outgoing) {
    }

    private final ChallengeRepository challenges;
    private final UserRepository users;
    private final MatchService matches;
    private final Clock clock = Clock.systemUTC();

    public ChallengeService(ChallengeRepository challenges, UserRepository users, MatchService matches) {
        this.challenges = challenges;
        this.users = users;
        this.matches = matches;
    }

    @Transactional
    public ChallengeView create(AuthUser actor, CreateChallengeRequest req) {
        Instant now = clock.instant();
        if (actor.id().equals(req.opponentId())) throw new ApiException(ErrorCode.MATCH_PLAYERS_SAME);
        GameConfig config;
        try {
            config = new GameConfig(req.startScore(), req.inRule(), req.outRule(), req.legsToWin());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        String message = TextInput.optionalSingleLine(req.message(), MAX_MESSAGE);
        User opponent = users.findById(req.opponentId()).filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED));
        // Serialise per challenger so two quick clicks can't both slip under the limits.
        users.findAllByIdForUpdate(List.of(actor.id()));
        challenges.expireDue(now);
        if (challenges.existsOpenBetween(actor.id(), opponent.getId(), now)) throw new ApiException(ErrorCode.CHALLENGE_EXISTS);
        if (challenges.countOpenFrom(actor.id(), now) >= MAX_OPEN_PER_PLAYER) throw new ApiException(ErrorCode.CHALLENGE_LIMIT);
        boolean rated = req.rated() == null || req.rated();
        Challenge c = challenges.save(new Challenge(actor.id(), opponent.getId(), config, rated, message, now, now.plus(ANSWER_WITHIN)));
        return view(c, null);
    }

    @Transactional
    public ChallengeList list(AuthUser actor) {
        challenges.expireDue(clock.instant());
        List<Challenge> open = challenges.findOpenFor(actor.id());
        Map<Long, User> who = people(open);
        return new ChallengeList(
                open.stream().filter(c -> c.getOpponentId().equals(actor.id())).map(c -> view(c, who)).toList(),
                open.stream().filter(c -> c.getChallengerId().equals(actor.id())).map(c -> view(c, who)).toList());
    }

    @Transactional
    public ChallengeView accept(Long id, AuthUser actor) {
        Challenge c = openFor(id, actor);
        if (!c.getOpponentId().equals(actor.id())) throw ApiException.forbidden();
        if (c.getStatus() != Challenge.Status.PENDING) throw new ApiException(ErrorCode.CHALLENGE_NOT_OPEN);
        Instant now = clock.instant();
        c.respond(Challenge.Status.ACCEPTED, now, now.plus(START_WITHIN));
        return view(c, null);
    }

    @Transactional
    public ChallengeView decline(Long id, AuthUser actor) {
        Challenge c = openFor(id, actor);
        if (!c.getOpponentId().equals(actor.id())) throw ApiException.forbidden();
        if (c.getStatus() != Challenge.Status.PENDING) throw new ApiException(ErrorCode.CHALLENGE_NOT_OPEN);
        c.respond(Challenge.Status.DECLINED, clock.instant(), null);
        return view(c, null);
    }

    /** The challenger can withdraw a pending challenge; either player can call off an accepted one. */
    @Transactional
    public ChallengeView cancel(Long id, AuthUser actor) {
        Challenge c = openFor(id, actor);
        if (c.getStatus() == Challenge.Status.PENDING && !c.getChallengerId().equals(actor.id())) throw ApiException.forbidden();
        c.close(Challenge.Status.CANCELLED);
        return view(c, null);
    }

    /** Either player presses Start: the match is created now, with a random starting player (bull-off skipped). */
    @Transactional
    public ChallengeView start(Long id, AuthUser actor) {
        Challenge c = openFor(id, actor);
        if (c.getStatus() != Challenge.Status.ACCEPTED) throw new ApiException(ErrorCode.CHALLENGE_NOT_OPEN);
        Match m = matches.createInternal(c.config(), c.getChallengerId(), c.getOpponentId(), null, c.isRated(), actor.id(), null);
        c.started(m.getId());
        return view(c, null);
    }

    /** Used when an account is deleted. */
    @Transactional
    public void cancelAllFor(Long userId) {
        challenges.cancelAllFor(userId);
    }

    /**
     * Loads and locks a challenge the actor is part of (404 for anyone else, so ids reveal nothing). A challenge past
     * its deadline is marked expired and refused.
     */
    private Challenge openFor(Long id, AuthUser actor) {
        Challenge c = challenges.findByIdForUpdate(id).filter(x -> x.involves(actor.id())).orElseThrow(ApiException::notFound);
        if (c.isOpen() && !c.getExpiresAt().isAfter(clock.instant())) {
            c.close(Challenge.Status.EXPIRED);
            challenges.flush();
        }
        if (!c.isOpen()) throw new ApiException(ErrorCode.CHALLENGE_NOT_OPEN);
        return c;
    }

    private Map<Long, User> people(List<Challenge> list) {
        Set<Long> ids = new HashSet<>();
        list.forEach(c -> {
            ids.add(c.getChallengerId());
            ids.add(c.getOpponentId());
        });
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ChallengeView view(Challenge c, Map<Long, User> who) {
        Map<Long, User> people = who != null ? who : people(List.of(c));
        return new ChallengeView(c.getId(), summary(people.get(c.getChallengerId())), summary(people.get(c.getOpponentId())),
                c.config().startScore(), c.config().inRule().name(), c.config().outRule().name(), c.config().legsToWin(), c.isRated(),
                c.getMessage(), c.getStatus().name(), c.getMatchId(), c.getCreatedAt(), c.getExpiresAt(), c.getRespondedAt());
    }

    private static PlayerSummary summary(User u) {
        return u == null ? null : PlayerSummary.of(u);
    }
}
