package no.trafondart.awards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

public final class AwardEntities {

    private AwardEntities() {
    }

    /** The awards that are handed out every month. */
    /** The monthly awards, plus PLAYER_OF_YEAR (stored separately in yearly_awards, with a "YYYY" period). */
    public enum Award { PLAYER_OF_MONTH, MOST_IMPROVED, MOST_ACTIVE, MOST_180S, HIGHEST_CHECKOUT, PLAYER_OF_YEAR }

    /** Marks a month as decided (even if nobody qualified), so it isn't recomputed on every request. */
    @Entity(name = "AwardPeriod")
    @Table(name = "award_periods")
    public static class Period {
        @Id
        @Column(length = 7)
        private String period;
        @Column(name = "finalized_at", nullable = false)
        private Instant finalizedAt = Instant.now();

        protected Period() {
        }

        public Period(String period) {
            this.period = period;
        }

        public String getPeriod() { return period; }
        public Instant getFinalizedAt() { return finalizedAt; }
    }

    @Entity(name = "MonthlyAward")
    @Table(name = "monthly_awards")
    public static class MonthlyAward {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(nullable = false, length = 7)
        private String period;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 24)
        private Award award;
        @Column(name = "user_id", nullable = false)
        private Long userId;
        @Column(nullable = false)
        private double value;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        protected MonthlyAward() {
        }

        public MonthlyAward(String period, Award award, Long userId, double value) {
            this.period = period;
            this.award = award;
            this.userId = userId;
            this.value = value;
        }

        public String getPeriod() { return period; }
        public Award getAward() { return award; }
        public Long getUserId() { return userId; }
        public double getValue() { return value; }
    }
}
