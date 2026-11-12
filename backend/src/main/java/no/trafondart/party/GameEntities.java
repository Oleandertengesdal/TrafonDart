package no.trafondart.party;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** JPA entities for party games. Kept together because they only make sense as a group. */
public final class GameEntities {

    private GameEntities() {
    }

    public enum Status { IN_PROGRESS, COMPLETED, ABANDONED }

    @Entity(name = "Game")
    @Table(name = "games")
    public static class Game {
        @Id
        private UUID id;

        @Enumerated(EnumType.STRING)
        @Column(name = "game_type", nullable = false, length = 20)
        private GameType type;

        @Column(name = "team_play", nullable = false)
        private boolean teamPlay;

        @Column(nullable = false, length = 1000)
        private String settings;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 16)
        private Status status = Status.IN_PROGRESS;

        @Column(name = "winner_side")
        private Integer winnerSide;

        @Column(name = "created_by", nullable = false)
        private Long createdBy;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        @Column(name = "completed_at")
        private Instant completedAt;

        @Version
        private long version;

        protected Game() {
        }

        public Game(GameType type, boolean teamPlay, String settings, Long createdBy) {
            this.id = UUID.randomUUID();
            this.type = type;
            this.teamPlay = teamPlay;
            this.settings = settings;
            this.createdBy = createdBy;
        }

        public UUID getId() { return id; }
        public GameType getType() { return type; }
        public boolean isTeamPlay() { return teamPlay; }
        public String getSettings() { return settings; }
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
        public Integer getWinnerSide() { return winnerSide; }
        public void setWinnerSide(Integer winnerSide) { this.winnerSide = winnerSide; }
        public Long getCreatedBy() { return createdBy; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    }

    @Embeddable
    public static class SlotId implements Serializable {
        @Column(name = "game_id")
        private UUID gameId;
        @Column(name = "slot")
        private int slot;

        protected SlotId() {
        }

        public SlotId(UUID gameId, int slot) {
            this.gameId = gameId;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SlotId s && s.slot == slot && Objects.equals(s.gameId, gameId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gameId, slot);
        }
    }

    @Entity(name = "GamePlayer")
    @Table(name = "game_players")
    public static class GamePlayer {
        @EmbeddedId
        private SlotId id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(nullable = false)
        private int side;

        protected GamePlayer() {
        }

        public GamePlayer(UUID gameId, int slot, Long userId, int side) {
            this.id = new SlotId(gameId, slot);
            this.userId = userId;
            this.side = side;
        }

        public UUID getGameId() { return id.gameId; }
        public int getSlot() { return id.slot; }
        public Long getUserId() { return userId; }
        public int getSide() { return side; }
    }

    @Embeddable
    public static class DartId implements Serializable {
        @Column(name = "game_id")
        private UUID gameId;
        @Column(name = "seq")
        private int seq;

        protected DartId() {
        }

        public DartId(UUID gameId, int seq) {
            this.gameId = gameId;
            this.seq = seq;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DartId d && d.seq == seq && Objects.equals(d.gameId, gameId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gameId, seq);
        }
    }

    @Entity(name = "GameDart")
    @Table(name = "game_darts")
    public static class GameDart {
        @EmbeddedId
        private DartId id;

        @Column(nullable = false)
        private int segment;

        @Column(nullable = false)
        private int multiplier;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        protected GameDart() {
        }

        public GameDart(UUID gameId, int seq, int segment, int multiplier) {
            this.id = new DartId(gameId, seq);
            this.segment = segment;
            this.multiplier = multiplier;
        }

        public int getSeq() { return id.seq; }
        public int getSegment() { return segment; }
        public int getMultiplier() { return multiplier; }
        public Instant getCreatedAt() { return createdAt; }
    }

    @Embeddable
    public static class ResultId implements Serializable {
        @Column(name = "game_id")
        private UUID gameId;
        @Column(name = "user_id")
        private Long userId;

        protected ResultId() {
        }

        public ResultId(UUID gameId, Long userId) {
            this.gameId = gameId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ResultId r && Objects.equals(r.gameId, gameId) && Objects.equals(r.userId, userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gameId, userId);
        }
    }

    @Entity(name = "GameResult")
    @Table(name = "game_results")
    public static class GameResult {
        @EmbeddedId
        private ResultId id;

        @Column(nullable = false)
        private int placement;

        @Column(name = "darts_thrown", nullable = false)
        private int dartsThrown;

        @Column(nullable = false)
        private int score;

        @Column(nullable = false)
        private boolean won;

        protected GameResult() {
        }

        public GameResult(UUID gameId, Long userId, int placement, int dartsThrown, int score, boolean won) {
            this.id = new ResultId(gameId, userId);
            this.placement = placement;
            this.dartsThrown = dartsThrown;
            this.score = score;
            this.won = won;
        }

        public UUID getGameId() { return id.gameId; }
        public Long getUserId() { return id.userId; }
        public int getPlacement() { return placement; }
        public int getDartsThrown() { return dartsThrown; }
        public int getScore() { return score; }
        public boolean isWon() { return won; }
    }
}
