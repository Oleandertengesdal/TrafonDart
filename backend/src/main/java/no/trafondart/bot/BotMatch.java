package no.trafondart.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import no.trafondart.game.GameConfig;
import no.trafondart.game.X01Engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A practice match against Trafo. The player is side 0, Trafo side 1. Stored apart from {@code matches}, so rating,
 * leaderboard, awards and every other competitive number can't see it. The player's numbers are copied in when the
 * match ends, for the bot record.
 */
@Entity
@Table(name = "bot_matches")
public class BotMatch {

    public static final int PLAYER = 0;
    public static final int BOT = 1;

    public enum Status { IN_PROGRESS, COMPLETED, FORFEITED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BotLevel level;

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

    @Column(name = "starting_player", nullable = false)
    private short startingPlayer;

    @Column(nullable = false)
    private long seed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.IN_PROGRESS;

    private Boolean won;

    @Column(name = "legs_won", nullable = false) private int legsWon;
    @Column(name = "legs_lost", nullable = false) private int legsLost;
    @Column(name = "darts_thrown", nullable = false) private int dartsThrown;
    @Column(name = "points_scored", nullable = false) private int pointsScored;
    @Column(name = "first9_darts", nullable = false) private int first9Darts;
    @Column(name = "first9_points", nullable = false) private int first9Points;
    @Column(name = "count_180", nullable = false) private int count180;
    @Column(name = "count_140", nullable = false) private int count140;
    @Column(name = "count_100", nullable = false) private int count100;
    @Column(name = "highest_turn", nullable = false) private int highestTurn;
    @Column(name = "highest_checkout", nullable = false) private int highestCheckout;
    @Column(name = "checkout_attempts", nullable = false) private int checkoutAttempts;
    @Column(name = "checkouts_hit", nullable = false) private int checkoutsHit;
    @Column(name = "bot_average", nullable = false, precision = 6, scale = 2) private BigDecimal botAverage = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected BotMatch() {
    }

    public BotMatch(Long userId, BotLevel level, GameConfig config, int startingPlayer, long seed) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.level = level;
        this.startScore = config.startScore();
        this.inRule = config.inRule();
        this.outRule = config.outRule();
        this.legsToWin = config.legsToWin();
        this.startingPlayer = (short) startingPlayer;
        this.seed = seed;
    }

    public GameConfig config() {
        return new GameConfig(startScore, inRule, outRule, legsToWin);
    }

    /** Ends the match – played out, or given up – and keeps the player's numbers for the record. */
    public void finish(Status how, boolean playerWon, X01Engine.MatchState state, Instant at) {
        this.status = how;
        this.won = playerWon;
        this.completedAt = at;
        X01Engine.PlayerStats me = state.stats()[PLAYER];
        this.legsWon = state.legsWon()[PLAYER];
        this.legsLost = state.legsWon()[BOT];
        this.dartsThrown = me.dartsThrown();
        this.pointsScored = me.pointsScored();
        this.first9Darts = me.first9Darts();
        this.first9Points = me.first9Points();
        this.count180 = me.count180();
        this.count140 = me.count140();
        this.count100 = me.count100();
        this.highestTurn = me.highestTurn();
        this.highestCheckout = me.highestCheckout();
        this.checkoutAttempts = me.checkoutAttempts();
        this.checkoutsHit = me.checkoutsHit();
        this.botAverage = BigDecimal.valueOf(state.stats()[BOT].threeDartAverage()).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public BotLevel getLevel() { return level; }
    public int getStartingPlayer() { return startingPlayer; }
    public long getSeed() { return seed; }
    public Status getStatus() { return status; }
    public Boolean getWon() { return won; }
    public int getLegsWon() { return legsWon; }
    public int getLegsLost() { return legsLost; }
    public int getDartsThrown() { return dartsThrown; }
    public int getPointsScored() { return pointsScored; }
    public int getFirst9Darts() { return first9Darts; }
    public int getFirst9Points() { return first9Points; }
    public int getCount180() { return count180; }
    public int getCount140() { return count140; }
    public int getCount100() { return count100; }
    public int getHighestTurn() { return highestTurn; }
    public int getHighestCheckout() { return highestCheckout; }
    public int getCheckoutAttempts() { return checkoutAttempts; }
    public int getCheckoutsHit() { return checkoutsHit; }
    public double getBotAverage() { return botAverage.doubleValue(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public double average() {
        return dartsThrown == 0 ? 0 : pointsScored * 3.0 / dartsThrown;
    }
}
