package no.trafondart.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "rating_before", nullable = false)
    private double ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private double ratingAfter;

    @Column(name = "rd_before", nullable = false)
    private double rdBefore;

    @Column(name = "rd_after", nullable = false)
    private double rdAfter;

    @Column(name = "volatility_after", nullable = false)
    private double volatilityAfter;

    @Column(name = "rated_at", nullable = false)
    private Instant ratedAt;

    protected RatingHistory() {
    }

    public RatingHistory(Long userId, UUID matchId, Glicko2.Rating before, Glicko2.Rating after, Instant ratedAt) {
        this.userId = userId;
        this.matchId = matchId;
        this.ratingBefore = before.rating();
        this.rdBefore = before.rd();
        this.ratingAfter = after.rating();
        this.rdAfter = after.rd();
        this.volatilityAfter = after.volatility();
        this.ratedAt = ratedAt;
    }

    public Long getUserId() { return userId; }
    public UUID getMatchId() { return matchId; }
    public double getRatingBefore() { return ratingBefore; }
    public double getRatingAfter() { return ratingAfter; }
    public double getRdBefore() { return rdBefore; }
    public double getRdAfter() { return rdAfter; }
    public Instant getRatedAt() { return ratedAt; }
}
