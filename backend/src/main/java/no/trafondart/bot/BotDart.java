package no.trafondart.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import no.trafondart.game.Dart;

import java.io.Serializable;
import java.util.UUID;

/** One dart in a bot match, the player's or Trafo's; who threw it follows from the order. */
@Entity
@Table(name = "bot_match_darts")
@IdClass(BotDart.Key.class)
public class BotDart {

    public record Key(UUID matchId, int seq) implements Serializable {
        public Key() {
            this(null, 0);
        }
    }

    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @Id
    private int seq;

    @Column(nullable = false)
    private int segment;

    @Column(nullable = false)
    private int multiplier;

    protected BotDart() {
    }

    public BotDart(UUID matchId, int seq, Dart dart) {
        this.matchId = matchId;
        this.seq = seq;
        this.segment = dart.segment();
        this.multiplier = dart.multiplier();
    }

    public Dart toDart() {
        return new Dart(segment, multiplier);
    }

    public UUID getMatchId() { return matchId; }
    public int getSeq() { return seq; }
}
