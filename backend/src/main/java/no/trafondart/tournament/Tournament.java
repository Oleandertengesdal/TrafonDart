package no.trafondart.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.trafondart.game.GameConfig;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournaments")
public class Tournament {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Format format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

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

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Tournament() {
    }

    public Tournament(String name, Format format, GameConfig config, boolean rated, Long createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.format = format;
        this.startScore = config.startScore();
        this.inRule = config.inRule();
        this.outRule = config.outRule();
        this.legsToWin = config.legsToWin();
        this.rated = rated;
        this.createdBy = createdBy;
    }

    public enum Format { ROUND_ROBIN, SINGLE_ELIMINATION }

    public enum Status { DRAFT, IN_PROGRESS, COMPLETED, CANCELLED }

    public GameConfig config() {
        return new GameConfig(startScore, inRule, outRule, legsToWin);
    }

    public void start() {
        this.status = Status.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void complete(Long winnerId) {
        this.status = Status.COMPLETED;
        this.winnerId = winnerId;
        this.completedAt = Instant.now();
    }

    public void reopen() {
        this.status = Status.IN_PROGRESS;
        this.winnerId = null;
        this.completedAt = null;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Format getFormat() { return format; }
    public Status getStatus() { return status; }
    public boolean isRated() { return rated; }
    public Long getCreatedBy() { return createdBy; }
    public Long getWinnerId() { return winnerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
