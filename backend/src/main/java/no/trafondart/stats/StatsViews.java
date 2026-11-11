package no.trafondart.stats;

import no.trafondart.user.UserDtos.PlayerSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StatsViews {

    private StatsViews() {
    }

    public record RatingPoint(Instant at, int rating, int rd, UUID matchId, String opponent, boolean won) {
    }

    public record AveragePoint(Instant at, UUID matchId, double average, double first9Average, boolean won) {
    }

    public record ProfileStats(
            PlayerSummary player,
            Instant memberSince,
            int matchesPlayed,
            int matchesWon,
            int matchesLost,
            double winRate,
            int legsWon,
            int legsLost,
            double legWinRate,
            double average,
            double first9Average,
            double bestMatchAverage,
            int count180,
            int count140,
            int count100,
            int highestTurn,
            int highestCheckout,
            int checkoutAttempts,
            int checkoutsHit,
            double checkoutPercentage,
            int peakRating,
            List<String> recentForm,
            int currentStreak,
            List<RatingPoint> ratingHistory,
            List<AveragePoint> averageTrend) {
    }

    public record HeadToHeadRow(PlayerSummary opponent, int played, int won, int lost, int legsWon, int legsLost,
                                double myAverage, double theirAverage, Instant lastPlayed) {
    }

    public record LeaderboardRow(int rank, PlayerSummary player, int played, int won, double winRate, double average,
                                 int count180, int highestCheckout) {
    }
}
