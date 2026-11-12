package no.trafondart.match;

import no.trafondart.common.TextInput;
import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig;
import no.trafondart.game.X01Engine;
import no.trafondart.rating.RatingService;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRepository matches;
    private final DartRepository darts;
    private final LegRecordRepository legs;
    private final PlayerMatchStatsRepository stats;
    private final DisputeRepository disputes;
    private final UserRepository users;
    private final MatchViewMapper mapper;
    private final RatingService ratings;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public MatchService(MatchRepository matches, DartRepository darts, LegRecordRepository legs,
                        PlayerMatchStatsRepository stats, DisputeRepository disputes, UserRepository users,
                        MatchViewMapper mapper, RatingService ratings, ApplicationEventPublisher events) {
        this.matches = matches;
        this.darts = darts;
        this.legs = legs;
        this.stats = stats;
        this.disputes = disputes;
        this.users = users;
        this.mapper = mapper;
        this.ratings = ratings;
        this.events = events;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public MatchViews.LiveMatchView create(AuthUser actor, MatchViews.CreateMatchRequest req) {
        GameConfig config = new GameConfig(req.startScore(), req.inRule(), req.outRule(), req.legsToWin());
        boolean rated = req.rated() == null || req.rated();
        if (rated && !actor.isAdmin() && !actor.id().equals(req.player1Id()) && !actor.id().equals(req.player2Id())) {
            // Anyone may score a casual match for friends, but rated matches must include the person creating them.
            throw ApiException.forbidden();
        }
        Match match = createInternal(config, req.player1Id(), req.player2Id(), req.startingPlayer(), rated, actor.id(), null);
        return mapper.live(match);
    }

    /** Also used by tournaments to start a fixture. */
    @Transactional
    public Match createInternal(GameConfig config, Long player1Id, Long player2Id, Integer startingPlayer,
                                boolean rated, Long createdBy, UUID tournamentId) {
        if (player1Id.equals(player2Id)) throw new ApiException(ErrorCode.MATCH_PLAYERS_SAME);
        requireActiveUser(player1Id);
        requireActiveUser(player2Id);
        int starter = startingPlayer != null ? startingPlayer : random.nextInt(2);
        Match match = matches.save(new Match(config, player1Id, player2Id, starter, rated, createdBy, tournamentId));
        events.publishEvent(new MatchEvents.MatchUpdated(mapper.live(match, X01Engine.replay(config, starter, List.of()))));
        events.publishEvent(new MatchEvents.FeedChanged(match.getId()));
        return match;
    }

    private void requireActiveUser(Long id) {
        User u = users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED));
        if (!u.isEnabled()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public MatchViews.LiveMatchView get(UUID id) {
        return mapper.live(matches.findById(id).orElseThrow(ApiException::notFound));
    }

    @Transactional(readOnly = true)
    public List<MatchViews.MatchSummary> live() {
        return mapper.summaries(matches.findAllByStatusOrderByCreatedAtDesc(Match.Status.IN_PROGRESS));
    }

    @Transactional(readOnly = true)
    public List<MatchViews.MatchSummary> recent(String username, int limit) {
        Long userId = null;
        if (username != null && !username.isBlank()) {
            userId = users.findByUsernameIgnoreCase(username).map(User::getId).orElseThrow(ApiException::notFound);
        }
        var statuses = EnumSet.of(Match.Status.COMPLETED, Match.Status.VOIDED);
        return mapper.summaries(matches.findRecent(userId, statuses, PageRequest.of(0, Math.clamp(limit, 1, 100))));
    }

    // ------------------------------------------------------------------ scoring

    @Transactional
    public MatchViews.LiveMatchView throwDart(UUID id, AuthUser actor, MatchViews.ThrowRequest req) {
        Match match = lockForScoring(id, actor);
        List<ThrownDart> recorded = darts.findAllByMatchIdOrderBySeqAsc(id);
        if (req.expectedDartCount() != recorded.size()) {
            throw new ApiException(ErrorCode.MATCH_OUT_OF_SYNC, mapper.live(match));
        }
        if (!Dart.isValid(req.segment(), req.multiplier())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Dart dart = new Dart(req.segment(), req.multiplier());

        List<Dart> sequence = new ArrayList<>(recorded.stream().map(ThrownDart::toDart).toList());
        X01Engine.MatchState before = X01Engine.replay(match.config(), match.getStartingPlayer(), sequence);
        Long thrower = match.playerId(before.currentPlayerIndex());
        sequence.add(dart);
        X01Engine.MatchState after = X01Engine.replay(match.config(), match.getStartingPlayer(), sequence);

        darts.save(new ThrownDart(id, recorded.size() + 1, thrower, dart));

        if (after.finished()) {
            completeMatch(match, after);
        }
        MatchViews.LiveMatchView view = mapper.live(match, after);
        events.publishEvent(new MatchEvents.MatchUpdated(view));
        return view;
    }

    @Transactional
    public MatchViews.LiveMatchView undo(UUID id, AuthUser actor, MatchViews.UndoRequest req) {
        Match match = lockForScoring(id, actor);
        List<ThrownDart> recorded = darts.findAllByMatchIdOrderBySeqAsc(id);
        if (recorded.isEmpty()) throw new ApiException(ErrorCode.MATCH_NOTHING_TO_UNDO);
        if (req.expectedDartCount() != recorded.size()) {
            throw new ApiException(ErrorCode.MATCH_OUT_OF_SYNC, mapper.live(match));
        }
        darts.delete(recorded.getLast());
        darts.flush();
        MatchViews.LiveMatchView view = mapper.live(match);
        events.publishEvent(new MatchEvents.MatchUpdated(view));
        return view;
    }

    @Transactional
    public MatchViews.LiveMatchView abandon(UUID id, AuthUser actor) {
        Match match = lockForScoring(id, actor);
        match.setStatus(Match.Status.ABANDONED);
        events.publishEvent(new MatchEvents.MatchAbandoned(match));
        MatchViews.LiveMatchView view = mapper.live(match);
        events.publishEvent(new MatchEvents.MatchUpdated(view));
        events.publishEvent(new MatchEvents.FeedChanged(id));
        return view;
    }

    private Match lockForScoring(UUID id, AuthUser actor) {
        Match match = matches.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        if (match.getStatus() != Match.Status.IN_PROGRESS) throw new ApiException(ErrorCode.MATCH_NOT_IN_PROGRESS);
        boolean scorer = match.involves(actor.id()) || match.getCreatedBy().equals(actor.id()) || actor.isAdmin();
        if (!scorer) throw new ApiException(ErrorCode.MATCH_NOT_SCORER);
        return match;
    }

    /** The completion pipeline: persist legs and stats, update ratings, notify tournaments. One transaction. */
    private void completeMatch(Match match, X01Engine.MatchState state) {
        Instant now = Instant.now();
        Long winnerId = match.playerId(state.winnerIndex());
        match.complete(winnerId, now);

        for (X01Engine.Leg leg : state.legs()) {
            if (leg.winnerIndex() == null) continue;
            legs.save(new MatchRecords.LegRecord(match.getId(), leg.legNo(), match.playerId(leg.starterIndex()),
                    match.playerId(leg.winnerIndex()), leg.dartsThrownBy(leg.winnerIndex()), leg.checkoutScore()));
        }
        for (int i = 0; i < X01Engine.PLAYERS; i++) {
            stats.save(new MatchRecords.PlayerMatchStats(match.getId(), match.playerId(i), state.stats()[i]));
        }
        if (match.isRated()) {
            ratings.applyMatch(match);
        }
        events.publishEvent(new MatchEvents.MatchCompleted(match));
        events.publishEvent(new MatchEvents.FeedChanged(match.getId()));
    }

    // ------------------------------------------------------------------ disputes

    @Transactional
    public MatchViews.DisputeView raiseDispute(UUID id, AuthUser actor, MatchViews.DisputeRequest req) {
        Match match = matches.findById(id).orElseThrow(ApiException::notFound);
        if (match.getStatus() != Match.Status.COMPLETED || !(match.involves(actor.id()) || actor.isAdmin())) {
            throw new ApiException(ErrorCode.DISPUTE_NOT_ALLOWED);
        }
        if (disputes.existsByMatchIdAndStatus(id, MatchRecords.Dispute.Status.OPEN)) {
            throw new ApiException(ErrorCode.DISPUTE_ALREADY_OPEN);
        }
        MatchRecords.Dispute d = disputes.save(new MatchRecords.Dispute(id, actor.id(), TextInput.multiLine(req.reason(), 500)));
        return disputeView(d);
    }

    @Transactional(readOnly = true)
    public List<MatchViews.DisputeView> listDisputes() {
        return disputes.findAllByOrderByCreatedAtDesc().stream().map(this::disputeView).toList();
    }

    @Transactional
    public MatchViews.DisputeView resolveDispute(Long disputeId, AuthUser admin, MatchViews.ResolveDisputeRequest req) {
        MatchRecords.Dispute d = disputes.findById(disputeId).orElseThrow(ApiException::notFound);
        if (d.getStatus() != MatchRecords.Dispute.Status.OPEN) throw new ApiException(ErrorCode.CONFLICT);
        String note = TextInput.optionalMultiLine(req.note(), 500);
        if (req.outcome() == MatchViews.ResolveDisputeRequest.Outcome.REJECT) {
            d.resolve(MatchRecords.Dispute.Status.REJECTED, admin.id(), note);
        } else {
            Match match = matches.findByIdForUpdate(d.getMatchId()).orElseThrow(ApiException::notFound);
            // Record the decision first and flush it: listeners below run bulk queries, and nothing they do may
            // lose this change (an upheld dispute that stays OPEN could be resolved a second time).
            d.resolve(MatchRecords.Dispute.Status.UPHELD, admin.id(), note);
            match.setStatus(Match.Status.VOIDED);
            disputes.flush();
            matches.flush();
            // Tournaments may refuse (e.g. the winner already played the next round) – that rolls everything back.
            events.publishEvent(new MatchEvents.MatchVoided(match));
            if (match.isRated()) ratings.recomputeAll();
            events.publishEvent(new MatchEvents.MatchUpdated(mapper.live(match)));
            events.publishEvent(new MatchEvents.FeedChanged(match.getId()));
        }
        return disputeView(d);
    }

    private MatchViews.DisputeView disputeView(MatchRecords.Dispute d) {
        String raisedBy = users.findById(d.getRaisedBy()).map(User::getUsername).orElse(null);
        String resolvedBy = d.getResolvedBy() == null ? null
                : users.findById(d.getResolvedBy()).map(User::getUsername).orElse(null);
        MatchViews.MatchSummary summary = matches.findById(d.getMatchId())
                .map(m -> mapper.summaries(List.of(m)).getFirst()).orElse(null);
        return new MatchViews.DisputeView(d.getId(), d.getMatchId(), raisedBy, d.getReason(), d.getStatus().name(),
                resolvedBy, d.getResolutionNote(), d.getCreatedAt(), d.getResolvedAt(), summary);
    }
}
