package no.trafondart.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.trafondart.game.Dart;

import java.time.Instant;
import java.util.UUID;

/** A persisted dart. The ordered list of these per match is the source of truth for the whole game. */
@Entity
@Table(name = "darts")
public class ThrownDart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private short segment;

    @Column(nullable = false)
    private short multiplier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ThrownDart() {
    }

    public ThrownDart(UUID matchId, int seq, Long playerId, Dart dart) {
        this.matchId = matchId;
        this.seq = seq;
        this.playerId = playerId;
        this.segment = (short) dart.segment();
        this.multiplier = (short) dart.multiplier();
    }

    public Dart toDart() {
        return new Dart(segment, multiplier);
    }

    public Long getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getSeq() { return seq; }
    public Long getPlayerId() { return playerId; }
}
