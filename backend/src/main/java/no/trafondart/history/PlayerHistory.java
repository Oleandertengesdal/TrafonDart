package no.trafondart.history;

import no.trafondart.game.X01Engine;
import no.trafondart.match.Match;
import no.trafondart.match.MatchRecords.LegRecord;
import no.trafondart.match.MatchRecords.PlayerMatchStats;
import no.trafondart.party.GameType;
import no.trafondart.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One player's finished matches and games in a period, in the order they were finished. */
public record PlayerHistory(User user, List<MatchEntry> matches, List<GameEntry> games) {

    /**
     * @param me              0 or 1: which side of the match the player was on
     * @param replay          the match replayed from its darts (legs, turns, finishing darts)
     * @param myRatingBefore  null for unrated matches
     * @param theirs          the opponent's stat row (legs won)
     */
    public record MatchEntry(Match match, int me, User opponent, boolean won, PlayerMatchStats mine, List<LegRecord> legs,
                             X01Engine.MatchState replay, Double myRatingBefore, Double opponentRatingBefore,
                             PlayerMatchStats theirs) {

        /** False for a match registered with only its result: it counts for wins and legs, not for throwing. */
        public boolean hasDarts() {
            return !match.isResultOnly() && mine != null && mine.getDartsThrown() > 0;
        }

        /** Legs in the match; a registered result has no leg records, only legs won on each side. */
        public int legsPlayed() {
            if (!legs.isEmpty() || !match.isResultOnly()) return legs.size();
            return (mine == null ? 0 : mine.getLegsWon()) + (theirs == null ? 0 : theirs.getLegsWon());
        }

        public double average() {
            return mine == null || mine.getDartsThrown() == 0 ? 0 : mine.getPointsScored() * 3.0 / mine.getDartsThrown();
        }

        public Instant at() {
            return match.getCompletedAt();
        }
    }

    public record GameEntry(UUID gameId, GameType type, boolean won, int placement, int darts, int score, Instant at) {
    }
}
