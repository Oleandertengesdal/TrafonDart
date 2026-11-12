package no.trafondart.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.trafondart.game.GameConfig;

import java.time.Instant;
import java.util.UUID;

/** "Fancy a game?" – a 1v1 X01 match one member offers another. The match is created only when it starts. */
@Entity
@Table(name = "challenges")
public class Challenge {

    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED, STARTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenger_id", nullable = false)
    private Long challengerId;

    @Column(name = "opponent_id", nullable = false)
    private Long opponentId;

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

    @Column(nullable = false)
    private boolean rated;

    @Column(length = 200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected Challenge() {
    }

    public Challenge(Long challengerId, Long opponentId, GameConfig config, boolean rated, String message, Instant now, Instant expiresAt) {
        this.challengerId = challengerId;
        this.opponentId = opponentId;
        this.startScore = config.startScore();
        this.inRule = config.inRule();
        this.outRule = config.outRule();
        this.legsToWin = config.legsToWin();
        this.rated = rated;
        this.message = message;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public GameConfig config() {
        return new GameConfig(startScore, inRule, outRule, legsToWin);
    }

    public boolean isOpen() {
        return status == Status.PENDING || status == Status.ACCEPTED;
    }

    public boolean involves(Long userId) {
        return challengerId.equals(userId) || opponentId.equals(userId);
    }

    void respond(Status to, Instant at, Instant newExpiry) {
        this.status = to;
        this.respondedAt = at;
        if (newExpiry != null) this.expiresAt = newExpiry;
    }

    void started(UUID matchId) {
        this.status = Status.STARTED;
        this.matchId = matchId;
    }

    void close(Status to) {
        this.status = to;
    }

    public Long getId() { return id; }
    public Long getChallengerId() { return challengerId; }
    public Long getOpponentId() { return opponentId; }
    public boolean isRated() { return rated; }
    public String getMessage() { return message; }
    public Status getStatus() { return status; }
    public UUID getMatchId() { return matchId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
