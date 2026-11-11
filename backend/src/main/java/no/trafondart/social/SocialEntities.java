package no.trafondart.social;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Kudos on matches, comments (one level of replies) and likes on comments. */
public final class SocialEntities {

    private SocialEntities() {
    }

    @Embeddable
    public static class KudosId implements Serializable {
        @Column(name = "match_id")
        private UUID matchId;
        @Column(name = "user_id")
        private Long userId;

        protected KudosId() {
        }

        public KudosId(UUID matchId, Long userId) {
            this.matchId = matchId;
            this.userId = userId;
        }

        public UUID getMatchId() { return matchId; }
        public Long getUserId() { return userId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof KudosId k && Objects.equals(matchId, k.matchId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchId, userId);
        }
    }

    @Entity(name = "MatchKudos")
    @Table(name = "match_kudos")
    public static class Kudos {
        @EmbeddedId
        private KudosId id;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        protected Kudos() {
        }

        public Kudos(UUID matchId, Long userId) {
            this.id = new KudosId(matchId, userId);
        }

        public KudosId getId() { return id; }
        public Instant getCreatedAt() { return createdAt; }
    }

    @Entity(name = "MatchComment")
    @Table(name = "match_comments")
    public static class Comment {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "match_id", nullable = false)
        private UUID matchId;
        @Column(name = "author_id", nullable = false)
        private Long authorId;
        @Column(name = "parent_id")
        private Long parentId;
        @Column(nullable = false, length = 1000)
        private String body;
        @Column(nullable = false)
        private boolean deleted;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();
        @Column(name = "edited_at")
        private Instant editedAt;

        protected Comment() {
        }

        public Comment(UUID matchId, Long authorId, Long parentId, String body) {
            this.matchId = matchId;
            this.authorId = authorId;
            this.parentId = parentId;
            this.body = body;
        }

        public void edit(String newBody) {
            this.body = newBody;
            this.editedAt = Instant.now();
        }

        /** Soft delete keeps the thread intact when there are replies; the text is wiped. */
        public void markDeleted() {
            this.deleted = true;
            this.body = "";
        }

        public Long getId() { return id; }
        public UUID getMatchId() { return matchId; }
        public Long getAuthorId() { return authorId; }
        public Long getParentId() { return parentId; }
        public String getBody() { return body; }
        public boolean isDeleted() { return deleted; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getEditedAt() { return editedAt; }
    }

    @Embeddable
    public static class LikeId implements Serializable {
        @Column(name = "comment_id")
        private Long commentId;
        @Column(name = "user_id")
        private Long userId;

        protected LikeId() {
        }

        public LikeId(Long commentId, Long userId) {
            this.commentId = commentId;
            this.userId = userId;
        }

        public Long getCommentId() { return commentId; }
        public Long getUserId() { return userId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof LikeId l && Objects.equals(commentId, l.commentId) && Objects.equals(userId, l.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(commentId, userId);
        }
    }

    @Entity(name = "CommentLike")
    @Table(name = "comment_likes")
    public static class CommentLike {
        @EmbeddedId
        private LikeId id;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        protected CommentLike() {
        }

        public CommentLike(Long commentId, Long userId) {
            this.id = new LikeId(commentId, userId);
        }

        public LikeId getId() { return id; }
    }
}
