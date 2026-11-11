package no.trafondart.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.trafondart.auth.AuthUser;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.match.Match;
import no.trafondart.match.MatchRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

/**
 * Kudos and comment threads on matches. Every change publishes {@link SocialChanged} so open match pages
 * refresh live.
 */
@Service
public class SocialService {

    public static final int MAX_COMMENT_LENGTH = 1000;

    /** Broadcast after commit on /topic/matches/{id}/social. */
    public record SocialChanged(UUID matchId) {
    }

    public record CommentRequest(@NotBlank @Size(max = MAX_COMMENT_LENGTH) String body, Long parentId) {
    }

    public record EditCommentRequest(@NotBlank @Size(max = MAX_COMMENT_LENGTH) String body) {
    }

    public record KudosView(int count, boolean mine, boolean canGive, List<PlayerSummary> givers) {
    }

    public record CommentView(Long id, PlayerSummary author, String body, boolean deleted, Instant createdAt,
                              Instant editedAt, int likes, boolean likedByMe, List<PlayerSummary> likedBy,
                              boolean canEdit, boolean canDelete, List<CommentView> replies) {
    }

    public record SocialView(UUID matchId, KudosView kudos, int commentCount, List<CommentView> comments) {
    }

    /** Kudos and comment counts for list rows. */
    public record Counts(int kudos, int comments) {
    }

    private final MatchRepository matches;
    private final KudosRepository kudos;
    private final CommentRepository comments;
    private final CommentLikeRepository likes;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public SocialService(MatchRepository matches, KudosRepository kudos, CommentRepository comments,
                         CommentLikeRepository likes, UserRepository users, ApplicationEventPublisher events) {
        this.matches = matches;
        this.kudos = kudos;
        this.comments = comments;
        this.likes = likes;
        this.users = users;
        this.events = events;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public SocialView view(UUID matchId, AuthUser viewer) {
        Match match = matches.findById(matchId).orElseThrow(ApiException::notFound);
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<SocialEntities.Kudos> k = kudos.findByMatch(matchId);
        boolean mine = k.stream().anyMatch(x -> x.getId().getUserId().equals(viewer.id()));
        KudosView kudosView = new KudosView(k.size(), mine, !match.involves(viewer.id()),
                k.stream().map(x -> userById.get(x.getId().getUserId())).filter(java.util.Objects::nonNull)
                        .map(PlayerSummary::of).toList());

        List<SocialEntities.Comment> all = comments.findByMatch(matchId);
        Map<Long, List<SocialEntities.CommentLike>> likesByComment = likes
                .findByCommentIds(all.stream().map(SocialEntities.Comment::getId).toList()).stream()
                .collect(Collectors.groupingBy(l -> l.getId().getCommentId()));

        Map<Long, List<SocialEntities.Comment>> repliesByParent = new HashMap<>();
        List<SocialEntities.Comment> topLevel = new ArrayList<>();
        for (SocialEntities.Comment c : all) {
            if (c.getParentId() == null) topLevel.add(c);
            else repliesByParent.computeIfAbsent(c.getParentId(), p -> new ArrayList<>()).add(c);
        }

        List<CommentView> views = new ArrayList<>();
        for (SocialEntities.Comment c : topLevel) {
            List<CommentView> replies = repliesByParent.getOrDefault(c.getId(), List.of()).stream()
                    .filter(r -> !r.isDeleted())
                    .map(r -> toView(r, List.of(), userById, likesByComment, viewer)).toList();
            if (c.isDeleted() && replies.isEmpty()) continue;
            views.add(toView(c, replies, userById, likesByComment, viewer));
        }
        int visible = (int) all.stream().filter(c -> !c.isDeleted()).count();
        return new SocialView(matchId, kudosView, visible, views);
    }

    private CommentView toView(SocialEntities.Comment c, List<CommentView> replies, Map<Long, User> userById,
                               Map<Long, List<SocialEntities.CommentLike>> likesByComment, AuthUser viewer) {
        List<SocialEntities.CommentLike> l = likesByComment.getOrDefault(c.getId(), List.of());
        User author = userById.get(c.getAuthorId());
        boolean own = c.getAuthorId().equals(viewer.id());
        return new CommentView(c.getId(), author == null ? null : PlayerSummary.of(author),
                c.isDeleted() ? "" : c.getBody(), c.isDeleted(), c.getCreatedAt(), c.getEditedAt(),
                l.size(), l.stream().anyMatch(x -> x.getId().getUserId().equals(viewer.id())),
                l.stream().map(x -> userById.get(x.getId().getUserId())).filter(java.util.Objects::nonNull)
                        .map(PlayerSummary::of).toList(),
                own && !c.isDeleted(), (own || viewer.isAdmin()) && !c.isDeleted(), replies);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Counts> counts(Collection<UUID> matchIds) {
        Map<UUID, Counts> out = new HashMap<>();
        if (matchIds.isEmpty()) return out;
        Map<UUID, Integer> k = new HashMap<>();
        for (Object[] row : kudos.countByMatchIds(matchIds)) k.put((UUID) row[0], ((Number) row[1]).intValue());
        Map<UUID, Integer> c = new HashMap<>();
        for (Object[] row : comments.countByMatchIds(matchIds)) c.put((UUID) row[0], ((Number) row[1]).intValue());
        Set<UUID> ids = new HashSet<>(matchIds);
        for (UUID id : ids) out.put(id, new Counts(k.getOrDefault(id, 0), c.getOrDefault(id, 0)));
        return out;
    }

    // ------------------------------------------------------------------ kudos

    @Transactional
    public SocialView giveKudos(UUID matchId, AuthUser actor) {
        Match match = matches.findById(matchId).orElseThrow(ApiException::notFound);
        if (match.involves(actor.id())) throw new ApiException(ErrorCode.KUDOS_OWN_MATCH);
        var id = new SocialEntities.KudosId(matchId, actor.id());
        if (!kudos.existsById(id)) kudos.save(new SocialEntities.Kudos(matchId, actor.id()));
        return changed(matchId, actor);
    }

    @Transactional
    public SocialView removeKudos(UUID matchId, AuthUser actor) {
        matches.findById(matchId).orElseThrow(ApiException::notFound);
        kudos.deleteById(new SocialEntities.KudosId(matchId, actor.id()));
        return changed(matchId, actor);
    }

    // ------------------------------------------------------------------ comments

    @Transactional
    public SocialView addComment(UUID matchId, AuthUser actor, CommentRequest req) {
        matches.findById(matchId).orElseThrow(ApiException::notFound);
        String body = clean(req.body());
        Long parentId = null;
        if (req.parentId() != null) {
            SocialEntities.Comment parent = comments.findById(req.parentId())
                    .filter(p -> p.getMatchId().equals(matchId))
                    .orElseThrow(ApiException::notFound);
            // Replying to a reply attaches to the same thread (one level deep, like most chat apps).
            parentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
        }
        comments.save(new SocialEntities.Comment(matchId, actor.id(), parentId, body));
        return changed(matchId, actor);
    }

    @Transactional
    public SocialView editComment(Long commentId, AuthUser actor, EditCommentRequest req) {
        SocialEntities.Comment c = comments.findById(commentId).orElseThrow(ApiException::notFound);
        if (!c.getAuthorId().equals(actor.id()) || c.isDeleted()) throw ApiException.forbidden();
        c.edit(clean(req.body()));
        return changed(c.getMatchId(), actor);
    }

    @Transactional
    public SocialView deleteComment(Long commentId, AuthUser actor) {
        SocialEntities.Comment c = comments.findById(commentId).orElseThrow(ApiException::notFound);
        if (!c.getAuthorId().equals(actor.id()) && !actor.isAdmin()) throw ApiException.forbidden();
        UUID matchId = c.getMatchId();
        if (c.getParentId() == null && comments.hasLiveReplies(c.getId())) {
            c.markDeleted(); // keep the thread, hide the text
        } else {
            Long parentId = c.getParentId();
            comments.delete(c);
            comments.flush();
            // A soft-deleted parent whose last reply is gone can go too.
            if (parentId != null) {
                comments.findById(parentId)
                        .filter(SocialEntities.Comment::isDeleted)
                        .filter(p -> !comments.hasLiveReplies(p.getId()))
                        .ifPresent(comments::delete);
            }
        }
        return changed(matchId, actor);
    }

    @Transactional
    public SocialView like(Long commentId, AuthUser actor) {
        SocialEntities.Comment c = comments.findById(commentId).filter(x -> !x.isDeleted()).orElseThrow(ApiException::notFound);
        var id = new SocialEntities.LikeId(commentId, actor.id());
        if (!likes.existsById(id)) likes.save(new SocialEntities.CommentLike(commentId, actor.id()));
        return changed(c.getMatchId(), actor);
    }

    @Transactional
    public SocialView unlike(Long commentId, AuthUser actor) {
        SocialEntities.Comment c = comments.findById(commentId).orElseThrow(ApiException::notFound);
        likes.deleteById(new SocialEntities.LikeId(commentId, actor.id()));
        return changed(c.getMatchId(), actor);
    }

    private SocialView changed(UUID matchId, AuthUser actor) {
        kudos.flush();
        comments.flush();
        likes.flush();
        events.publishEvent(new SocialChanged(matchId));
        return view(matchId, actor);
    }

    private static String clean(String body) {
        String trimmed = body == null ? "" : body.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_COMMENT_LENGTH) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        return trimmed;
    }
}
