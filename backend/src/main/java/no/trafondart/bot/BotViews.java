package no.trafondart.bot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import no.trafondart.game.GameConfig;
import no.trafondart.match.MatchViews;
import no.trafondart.user.UserDtos.PlayerSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for practice matches against Trafo. */
public final class BotViews {

    private BotViews() {
    }

    /** @param startingPlayer 0 = you throw first, 1 = Trafo, null = random */
    public record CreateBotMatchRequest(
            @NotNull BotLevel level,
            @NotNull Integer startScore,
            @NotNull GameConfig.InRule inRule,
            @NotNull GameConfig.OutRule outRule,
            @NotNull @Min(1) @Max(GameConfig.MAX_LEGS_TO_WIN) Integer legsToWin,
            @Min(0) @Max(1) Integer startingPlayer) {
    }

    /**
     * The scoreboard: the same fields as a match ({@link MatchViews.LiveMatchView}), so the same components can show
     * it, plus the level, {@code playerTurn} (your latest turn, finished or not) and {@code botTurns}: Trafo's turns
     * since your last dart, for showing them dart by dart.
     */
    public record BotMatchView(
            UUID id,
            String status,
            String level,
            int typicalAverage,
            MatchViews.ConfigView config,
            boolean rated,
            MatchViews.TournamentRef tournament,
            List<PlayerSummary> players,
            List<Long> scorerIds,
            int currentLeg,
            int currentPlayerIndex,
            int[] legsWon,
            int[] remaining,
            boolean[] opened,
            MatchViews.TurnView currentTurn,
            MatchViews.TurnView lastCompletedTurn,
            boolean turnJustEnded,
            List<MatchViews.LivePlayerStats> stats,
            List<String> checkout,
            int dartsLeftInTurn,
            String lastEvent,
            Integer winnerIndex,
            int dartCount,
            List<MatchViews.LegView> legs,
            MatchViews.TurnView playerTurn,
            List<MatchViews.TurnView> botTurns,
            Instant createdAt,
            Instant completedAt,
            boolean disputed) {
    }

    public record LevelRecord(String level, int typicalAverage, int played, int won) {
    }

    /** Your throwing in bot matches, and Trafo's average against you. */
    public record Throwing(double average, double first9Average, double bestAverage, int count180, int count140, int count100,
                           int highestTurn, int highestCheckout, int checkoutAttempts, int checkoutsHit,
                           double checkoutPercentage, double trafoAverage) {
    }

    public record RecentBotMatch(UUID id, String level, String status, boolean won, int legsWon, int legsLost,
                                 double average, double trafoAverage, int startScore, Instant completedAt) {
    }

    /**
     * A player's record against Trafo. Kept apart from the match record: wins here never count as match wins.
     *
     * @param currentStreak wins in a row, up to the latest match (0 after a loss)
     * @param hardestBeaten the highest level beaten, or null
     * @param openMatchId   your own unfinished match against Trafo (always null on other players' records)
     */
    public record BotRecord(PlayerSummary player, int played, int won, int lost, double winRate, int currentStreak,
                            int bestStreak, String hardestBeaten, List<LevelRecord> levels, Throwing throwing,
                            List<RecentBotMatch> recent, UUID openMatchId) {
    }
}
