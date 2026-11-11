package no.trafondart.match;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.trafondart.game.X01Engine;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Rows written when a match completes, plus disputes. Grouped here because each is a small, plain table. */
public final class MatchRecords {

    private MatchRecords() {
    }

    @Entity(name = "LegRecord")
    @Table(name = "legs")
    public static class LegRecord {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "match_id", nullable = false)
        private UUID matchId;
        @Column(name = "leg_no", nullable = false)
        private int legNo;
        @Column(name = "starter_id", nullable = false)
        private Long starterId;
        @Column(name = "winner_id", nullable = false)
        private Long winnerId;
        @Column(name = "winner_darts", nullable = false)
        private int winnerDarts;
        @Column(nullable = false)
        private int checkout;

        protected LegRecord() {
        }

        public LegRecord(UUID matchId, int legNo, Long starterId, Long winnerId, int winnerDarts, int checkout) {
            this.matchId = matchId;
            this.legNo = legNo;
            this.starterId = starterId;
            this.winnerId = winnerId;
            this.winnerDarts = winnerDarts;
            this.checkout = checkout;
        }

        public UUID getMatchId() { return matchId; }
        public int getLegNo() { return legNo; }
        public Long getStarterId() { return starterId; }
        public Long getWinnerId() { return winnerId; }
        public int getWinnerDarts() { return winnerDarts; }
        public int getCheckout() { return checkout; }
    }

    @Embeddable
    public static class StatsId implements Serializable {
        @Column(name = "match_id")
        private UUID matchId;
        @Column(name = "user_id")
        private Long userId;

        protected StatsId() {
        }

        public StatsId(UUID matchId, Long userId) {
            this.matchId = matchId;
            this.userId = userId;
        }

        public UUID getMatchId() { return matchId; }
        public Long getUserId() { return userId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof StatsId s && Objects.equals(matchId, s.matchId) && Objects.equals(userId, s.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchId, userId);
        }
    }

    @Entity(name = "PlayerMatchStats")
    @Table(name = "match_player_stats")
    public static class PlayerMatchStats {
        @EmbeddedId
        private StatsId id;
        @Column(name = "legs_won") private int legsWon;
        @Column(name = "darts_thrown") private int dartsThrown;
        @Column(name = "points_scored") private int pointsScored;
        @Column(name = "first9_darts") private int first9Darts;
        @Column(name = "first9_points") private int first9Points;
        @Column(name = "count_180") private int count180;
        @Column(name = "count_140") private int count140;
        @Column(name = "count_100") private int count100;
        @Column(name = "highest_turn") private int highestTurn;
        @Column(name = "highest_checkout") private int highestCheckout;
        @Column(name = "checkout_attempts") private int checkoutAttempts;
        @Column(name = "checkouts_hit") private int checkoutsHit;

        protected PlayerMatchStats() {
        }

        public PlayerMatchStats(UUID matchId, Long userId, X01Engine.PlayerStats s) {
            this.id = new StatsId(matchId, userId);
            this.legsWon = s.legsWon();
            this.dartsThrown = s.dartsThrown();
            this.pointsScored = s.pointsScored();
            this.first9Darts = s.first9Darts();
            this.first9Points = s.first9Points();
            this.count180 = s.count180();
            this.count140 = s.count140();
            this.count100 = s.count100();
            this.highestTurn = s.highestTurn();
            this.highestCheckout = s.highestCheckout();
            this.checkoutAttempts = s.checkoutAttempts();
            this.checkoutsHit = s.checkoutsHit();
        }

        public StatsId getId() { return id; }
        public int getLegsWon() { return legsWon; }
        public int getDartsThrown() { return dartsThrown; }
        public int getPointsScored() { return pointsScored; }
        public int getFirst9Darts() { return first9Darts; }
        public int getFirst9Points() { return first9Points; }
        public int getCount180() { return count180; }
        public int getCount140() { return count140; }
        public int getCount100() { return count100; }
        public int getHighestTurn() { return highestTurn; }
        public int getHighestCheckout() { return highestCheckout; }
        public int getCheckoutAttempts() { return checkoutAttempts; }
        public int getCheckoutsHit() { return checkoutsHit; }
    }

    @Entity(name = "Dispute")
    @Table(name = "match_disputes")
    public static class Dispute {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "match_id", nullable = false)
        private UUID matchId;
        @Column(name = "raised_by", nullable = false)
        private Long raisedBy;
        @Column(nullable = false, length = 500)
        private String reason;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 16)
        private Status status = Status.OPEN;
        @Column(name = "resolved_by")
        private Long resolvedBy;
        @Column(name = "resolution_note", length = 500)
        private String resolutionNote;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();
        @Column(name = "resolved_at")
        private Instant resolvedAt;

        public enum Status { OPEN, REJECTED, UPHELD }

        protected Dispute() {
        }

        public Dispute(UUID matchId, Long raisedBy, String reason) {
            this.matchId = matchId;
            this.raisedBy = raisedBy;
            this.reason = reason;
        }

        public void resolve(Status outcome, Long adminId, String note) {
            this.status = outcome;
            this.resolvedBy = adminId;
            this.resolutionNote = note;
            this.resolvedAt = Instant.now();
        }

        public Long getId() { return id; }
        public UUID getMatchId() { return matchId; }
        public Long getRaisedBy() { return raisedBy; }
        public String getReason() { return reason; }
        public Status getStatus() { return status; }
        public Long getResolvedBy() { return resolvedBy; }
        public String getResolutionNote() { return resolutionNote; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getResolvedAt() { return resolvedAt; }
    }
}
