package no.trafondart.awards;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Player of the Year: a decided year, and its winner. */
public final class YearEntities {

    private YearEntities() {
    }

    @Entity(name = "AwardYear")
    @Table(name = "award_years")
    public static class AwardYear {
        @Id
        private Integer year;

        @Column(name = "finalized_at", nullable = false)
        private Instant finalizedAt = Instant.now();

        protected AwardYear() {
        }

        public AwardYear(int year) {
            this.year = year;
        }

        public Integer getYear() { return year; }
    }

    @Embeddable
    public static class YearlyAwardId implements Serializable {
        @Column(name = "year")
        private Integer year;
        @Column(name = "award", length = 24)
        private String award;

        protected YearlyAwardId() {
        }

        public YearlyAwardId(int year, String award) {
            this.year = year;
            this.award = award;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof YearlyAwardId y && Objects.equals(y.year, year) && Objects.equals(y.award, award);
        }

        @Override
        public int hashCode() {
            return Objects.hash(year, award);
        }
    }

    @Entity(name = "YearlyAward")
    @Table(name = "yearly_awards")
    public static class YearlyAward {
        @EmbeddedId
        private YearlyAwardId id;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(nullable = false)
        private double value;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        protected YearlyAward() {
        }

        public YearlyAward(int year, Long userId, double value) {
            this.id = new YearlyAwardId(year, AwardEntities.Award.PLAYER_OF_YEAR.name());
            this.userId = userId;
            this.value = value;
        }

        public int getYear() { return id.year; }
        public AwardEntities.Award getAward() { return AwardEntities.Award.valueOf(id.award); }
        public Long getUserId() { return userId; }
        public double getValue() { return value; }
    }
}
