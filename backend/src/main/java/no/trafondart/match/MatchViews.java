package no.trafondart.match;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.trafondart.game.GameConfig;
import no.trafondart.user.UserDtos.PlayerSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for the match API and the live socket. */
public final class MatchViews {

    private MatchViews() {
    }

    public record ConfigView(int startScore, String inRule, String outRule, int legsToWin) {
        public static ConfigView of(GameConfig c) {
            return new ConfigView(c.startScore(), c.inRule().name(), c.outRule().name(), c.legsToWin());
        }
    }

    public record CreateMatchRequest(
            @NotNull Long player1Id,
            @NotNull Long player2Id,
            @NotNull Integer startScore,
            @NotNull GameConfig.InRule inRule,
            @NotNull GameConfig.OutRule outRule,
            @NotNull @Min(1) @Max(GameConfig.MAX_LEGS_TO_WIN) Integer legsToWin,
            /* 0 = player 1 throws first, 1 = player 2, null = random (bull-off skipped) */
            @Min(0) @Max(1) Integer startingPlayer,
            Boolean rated) {
    }

    /**
     * @param expectedDartCount the number of darts the client believes are already recorded. Protects against
     *                          double taps and two devices scoring at once: if it does not match, the request is
     *                          rejected with MATCH_OUT_OF_SYNC and the current state.
     */
    public record ThrowRequest(@NotNull @Min(0) @Max(25) Integer segment,
                               @NotNull @Min(0) @Max(3) Integer multiplier,
                               @NotNull @Min(0) Integer expectedDartCount) {
    }

    public record UndoRequest(@NotNull @Min(1) Integer expectedDartCount) {
    }

    public record DisputeRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record DartView(int segment, int multiplier, String label, int points) {
    }

    public record TurnView(int playerIndex, List<DartView> darts, int total, boolean bust, boolean checkout) {
    }

    public record LivePlayerStats(double average, double first9Average, int dartsThrown, int pointsScored,
                                  int count180, int count140, int count100, int highestTurn, int highestCheckout,
                                  int checkoutAttempts, int checkoutsHit, double checkoutPercentage) {
    }

    public record LegView(int legNo, int starterIndex, Integer winnerIndex, int winnerDarts, Integer checkout) {
    }

    public record TournamentRef(UUID id, String name) {
    }

    /** Everything the scoreboard needs, pushed on /topic/matches/{id} after every dart. */
    public record LiveMatchView(
            UUID id,
            String status,
            ConfigView config,
            boolean rated,
            TournamentRef tournament,
            List<PlayerSummary> players,
            List<Long> scorerIds,
            int currentLeg,
            int currentPlayerIndex,
            int[] legsWon,
            int[] remaining,
            boolean[] opened,
            TurnView currentTurn,
            TurnView lastCompletedTurn,
            boolean turnJustEnded,
            List<LivePlayerStats> stats,
            List<String> checkout,
            int dartsLeftInTurn,
            String lastEvent,
            Integer winnerIndex,
            int dartCount,
            List<LegView> legs,
            Instant createdAt,
            Instant completedAt,
            boolean disputed
    ) {
    }

    /** Compact row for lists (home page, history, profile). */
    public record MatchSummary(UUID id, String status, ConfigView config, boolean rated,
                               List<PlayerSummary> players, int[] legsWon, double[] averages,
                               Integer winnerIndex, Instant createdAt, Instant completedAt,
                               TournamentRef tournament, int kudos, int comments) {
    }

    public record DisputeView(Long id, UUID matchId, String raisedBy, String reason, String status,
                              String resolvedBy, String resolutionNote, Instant createdAt, Instant resolvedAt,
                              MatchSummary match) {
    }

    public record ResolveDisputeRequest(@NotNull Outcome outcome, @Size(max = 500) String note) {
        public enum Outcome { REJECT, VOID_MATCH }
    }
}
