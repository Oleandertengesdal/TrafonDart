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

    /** A finished normal match nobody has confirmed or disputed confirms itself after this long. */
    public static final java.time.Duration CONFIRM_AFTER = java.time.Duration.ofDays(2);

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

    /** Registered with only the result: no darts. */
    @Column(name = "result_only", nullable = false)
    private boolean resultOnly;

    /** When the match started to count. Ratings are applied and rebuilt in this order. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Who confirmed it; null when it confirmed itself (or counted before confirmation existed). */
    @Column(name = "confirmed_by")
    private Long confirmedBy;

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

    /** A result registered without darts. Waits for confirmation like any finished normal match. */
    public static Match resultOnly(GameConfig config, Long player1Id, Long player2Id, boolean rated, Long createdBy,
                                   Long winnerId, Instant playedAt) {
        Match m = new Match(config, player1Id, player2Id, 0, rated, createdBy, null);
        m.resultOnly = true;
        m.finish(winnerId, playedAt);
        return m;
    }

    /**
     * IN_PROGRESS → UNCONFIRMED (finished, not counted yet) → COMPLETED (counts). ABANDONED: stopped before the end.
     * VOIDED: an admin upheld a dispute.
     */
    public enum Status { IN_PROGRESS, UNCONFIRMED, COMPLETED, ABANDONED, VOIDED }

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

    /** The last dart (or the registered result): there is a winner, but the match doesn't count until confirmed. */
    public void finish(Long winnerId, Instant at) {
        this.status = Status.UNCONFIRMED;
        this.winnerId = winnerId;
        this.completedAt = at;
    }

    /** Starts to count. {@code by} is null when it confirmed itself. */
    public void confirm(Long by, Instant at) {
        this.status = Status.COMPLETED;
        this.confirmedBy = by;
        this.confirmedAt = at;
    }

    /** Tournament matches count as soon as they are won. */
    public void complete(Long winnerId, Instant at) {
        finish(winnerId, at);
        confirm(null, at);
    }

    /**
     * The players who may confirm the match: the one who didn't register (create) it, or either of them if someone
     * else did. Admins may always confirm, and aren't listed.
     */
    public java.util.List<Long> confirmerIds() {
        if (createdBy.equals(player1Id)) return java.util.List.of(player2Id);
        if (createdBy.equals(player2Id)) return java.util.List.of(player1Id);
        return java.util.List.of(player1Id, player2Id);
    }

    /** Tournament matches count when they're won; every other match is confirmed. */
    public boolean needsConfirmation() {
        return tournamentId == null;
    }

    /** When the rating changes: when the match was confirmed (before 1.7.0: when it finished). */
    public Instant countedAt() {
        return confirmedAt != null ? confirmedAt : completedAt;
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
    public boolean isResultOnly() { return resultOnly; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
}
