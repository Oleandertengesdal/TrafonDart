package no.trafondart.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import no.trafondart.game.GameConfig;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    private UUID id;

    @Column(name = "start_score", nullable = false)
    private int startScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "in_rule", nullable = false, length = 16)
    private GameConfig.InRule inRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "out_rule", nullable = false, length = 16)
    private GameConfig.OutRule outRule;

    @Column(name = "legs_to_win", nullable = false)
    private int legsToWin;

    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    @Column(name = "starting_player", nullable = false)
    private short startingPlayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.IN_PROGRESS;

    @Column(nullable = false)
    private boolean rated;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "tournament_id")
    private UUID tournamentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected Match() {
    }

    public Match(GameConfig config, Long player1Id, Long player2Id, int startingPlayer, boolean rated,
                 Long createdBy, UUID tournamentId) {
        this.id = UUID.randomUUID();
        this.startScore = config.startScore();
        this.inRule = config.inRule();
        this.outRule = config.outRule();
        this.legsToWin = config.legsToWin();
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.startingPlayer = (short) startingPlayer;
        this.rated = rated;
        this.createdBy = createdBy;
        this.tournamentId = tournamentId;
    }

    public enum Status { IN_PROGRESS, COMPLETED, ABANDONED, VOIDED }

    public GameConfig config() {
        return new GameConfig(startScore, inRule, outRule, legsToWin);
    }

    public Long playerId(int index) {
        return index == 0 ? player1Id : player2Id;
    }

    public int indexOf(Long userId) {
        if (player1Id.equals(userId)) return 0;
        if (player2Id.equals(userId)) return 1;
        return -1;
    }

    public boolean involves(Long userId) {
        return indexOf(userId) >= 0;
    }

    public Long opponentOf(Long userId) {
        return player1Id.equals(userId) ? player2Id : player1Id;
    }

    public void complete(Long winnerId, Instant at) {
        this.status = Status.COMPLETED;
        this.winnerId = winnerId;
        this.completedAt = at;
    }

    public UUID getId() { return id; }
    public int getStartScore() { return startScore; }
    public GameConfig.InRule getInRule() { return inRule; }
    public GameConfig.OutRule getOutRule() { return outRule; }
    public int getLegsToWin() { return legsToWin; }
    public Long getPlayer1Id() { return player1Id; }
    public Long getPlayer2Id() { return player2Id; }
    public int getStartingPlayer() { return startingPlayer; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isRated() { return rated; }
    public Long getWinnerId() { return winnerId; }
    public Long getCreatedBy() { return createdBy; }
    public UUID getTournamentId() { return tournamentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
}
