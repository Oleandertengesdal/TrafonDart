package no.trafondart.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tournament_participants")
public class TournamentParticipant {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private int seed;

    protected TournamentParticipant() {
    }

    public TournamentParticipant(UUID tournamentId, Long userId, int seed) {
        this.id = new Key(tournamentId, userId);
        this.seed = seed;
    }

    public Long userId() {
        return id.userId;
    }

    public int getSeed() { return seed; }
    public void setSeed(int seed) { this.seed = seed; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "tournament_id")
        private UUID tournamentId;
        @Column(name = "user_id")
        private Long userId;

        protected Key() {
        }

        public Key(UUID tournamentId, Long userId) {
            this.tournamentId = tournamentId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tournamentId, k.tournamentId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tournamentId, userId);
        }
    }
}
