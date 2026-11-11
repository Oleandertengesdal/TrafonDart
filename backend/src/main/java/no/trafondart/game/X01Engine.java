package no.trafondart.game;

import no.trafondart.game.GameConfig.InRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure X01 rules engine for a two-player match.
 *
 * <p>The list of darts thrown is the single source of truth. {@link #replay} rebuilds the whole match –
 * turns, busts, legs, winner and statistics – from that list. This makes "undo" trivially correct (drop the
 * last dart, replay) even across leg boundaries, and keeps the rules testable without Spring or a database.
 */
public final class X01Engine {

    public static final int PLAYERS = 2;
    public static final int DARTS_PER_TURN = 3;

    private X01Engine() {
    }

    /**
     * @param config         match rules
     * @param startingPlayer 0 or 1 – who throws first in leg 1 (legs then alternate)
     * @param darts          every dart thrown so far, in order
     * @throws IllegalStateException if a dart is thrown after the match was already won
     */
    public static MatchState replay(GameConfig config, int startingPlayer, List<Dart> darts) {
        if (startingPlayer != 0 && startingPlayer != 1) {
            throw new IllegalArgumentException("startingPlayer must be 0 or 1");
        }
        Replay r = new Replay(config, startingPlayer);
        for (Dart dart : darts) {
            r.apply(dart);
        }
        return r.finish(darts.size());
    }

    // ------------------------------------------------------------------------------------------

    /** A dart and what it did. {@code counted} is 0 for darts before a double-in opening. */
    public record DartResult(Dart dart, int counted, int remainingAfter, boolean checkoutAttempt) {
    }

    public static final class Turn {
        private final int legNo;
        private final int playerIndex;
        private final int startRemaining;
        private final int turnInLeg; // 1-based, per player
        private final List<DartResult> darts = new ArrayList<>(DARTS_PER_TURN);
        private boolean bust;
        private boolean checkout;

        Turn(int legNo, int playerIndex, int startRemaining, int turnInLeg) {
            this.legNo = legNo;
            this.playerIndex = playerIndex;
            this.startRemaining = startRemaining;
            this.turnInLeg = turnInLeg;
        }

        public int legNo() { return legNo; }
        public int playerIndex() { return playerIndex; }
        public int startRemaining() { return startRemaining; }
        public int turnInLeg() { return turnInLeg; }
        public List<DartResult> darts() { return darts; }
        public boolean bust() { return bust; }
        public boolean checkout() { return checkout; }

        public boolean complete() {
            return bust || checkout || darts.size() == DARTS_PER_TURN;
        }

        /** Points this turn took off the score (0 if bust). */
        public int scored() {
            if (bust) return 0;
            return darts.stream().mapToInt(DartResult::counted).sum();
        }

        /** Running total shown while the turn is in progress (before a bust is known it is the raw sum). */
        public int runningTotal() {
            return darts.stream().mapToInt(DartResult::counted).sum();
        }

        public int endRemaining() {
            return bust ? startRemaining : startRemaining - scored();
        }
    }

    public static final class Leg {
        private final int legNo;
        private final int starterIndex;
        private final List<Turn> turns = new ArrayList<>();
        private Integer winnerIndex;

        Leg(int legNo, int starterIndex) {
            this.legNo = legNo;
            this.starterIndex = starterIndex;
        }

        public int legNo() { return legNo; }
        public int starterIndex() { return starterIndex; }
        public List<Turn> turns() { return turns; }
        public Integer winnerIndex() { return winnerIndex; }

        public int dartsThrownBy(int playerIndex) {
            return turns.stream().filter(t -> t.playerIndex == playerIndex).mapToInt(t -> t.darts.size()).sum();
        }

        /** Score of the checkout turn, or null if the leg is not finished. */
        public Integer checkoutScore() {
            if (winnerIndex == null) return null;
            return turns.getLast().scored();
        }
    }

    public static final class PlayerStats {
        private int legsWon;
        private int dartsThrown;
        private int pointsScored;
        private int first9Darts;
        private int first9Points;
        private int count180;
        private int count140;
        private int count100;
        private int highestTurn;
        private int highestCheckout;
        private int checkoutAttempts;
        private int checkoutsHit;

        public int legsWon() { return legsWon; }
        public int dartsThrown() { return dartsThrown; }
        public int pointsScored() { return pointsScored; }
        public int first9Darts() { return first9Darts; }
        public int first9Points() { return first9Points; }
        public int count180() { return count180; }
        /** Turns scoring 140–179. */
        public int count140() { return count140; }
        /** Turns scoring 100–139. */
        public int count100() { return count100; }
        public int highestTurn() { return highestTurn; }
        public int highestCheckout() { return highestCheckout; }
        public int checkoutAttempts() { return checkoutAttempts; }
        public int checkoutsHit() { return checkoutsHit; }

        public double threeDartAverage() {
            return dartsThrown == 0 ? 0 : pointsScored * 3.0 / dartsThrown;
        }

        public double first9Average() {
            return first9Darts == 0 ? 0 : first9Points * 3.0 / first9Darts;
        }

        public double checkoutPercentage() {
            return checkoutAttempts == 0 ? 0 : checkoutsHit * 100.0 / checkoutAttempts;
        }
    }

    /** What the most recent dart caused – drives sounds and animations on the client. */
    public enum EventType { NONE, DART, BUST, LEG_WON, MATCH_WON }

    public record MatchState(
            GameConfig config,
            int startingPlayer,
            List<Leg> legs,
            int[] legsWon,
            int[] remaining,
            boolean[] opened,
            int currentPlayerIndex,
            Turn currentTurn,
            Turn lastCompletedTurn,
            Integer winnerIndex,
            PlayerStats[] stats,
            EventType lastEvent,
            int dartCount
    ) {
        public boolean finished() {
            return winnerIndex != null;
        }

        public Leg currentLeg() {
            return legs.getLast();
        }

        public int dartsLeftInTurn() {
            return currentTurn == null ? DARTS_PER_TURN : DARTS_PER_TURN - currentTurn.darts().size();
        }
    }

    // ------------------------------------------------------------------------------------------

    private static final class Replay {
        private final GameConfig config;
        private final int startingPlayer;
        private final List<Leg> legs = new ArrayList<>();
        private final int[] legsWon = new int[PLAYERS];
        private final int[] remaining = new int[PLAYERS];
        private final boolean[] opened = new boolean[PLAYERS];
        private final int[] turnsInLeg = new int[PLAYERS];
        private final PlayerStats[] stats = {new PlayerStats(), new PlayerStats()};
        private int current;
        private Turn turn;
        private Turn lastCompleted;
        private Integer winner;
        private EventType lastEvent = EventType.NONE;

        Replay(GameConfig config, int startingPlayer) {
            this.config = config;
            this.startingPlayer = startingPlayer;
            startLeg();
        }

        private void startLeg() {
            int legNo = legs.size() + 1;
            int starter = (legNo % 2 == 1) ? startingPlayer : 1 - startingPlayer;
            legs.add(new Leg(legNo, starter));
            for (int p = 0; p < PLAYERS; p++) {
                remaining[p] = config.startScore();
                opened[p] = config.inRule() == InRule.STRAIGHT;
                turnsInLeg[p] = 0;
            }
            current = starter;
            turn = null;
        }

        void apply(Dart dart) {
            if (winner != null) {
                throw new IllegalStateException("Match is already finished");
            }
            Leg leg = legs.getLast();
            if (turn == null) {
                turnsInLeg[current]++;
                turn = new Turn(leg.legNo, current, remaining[current], turnsInLeg[current]);
                leg.turns.add(turn);
            }
            PlayerStats ps = stats[current];
            int before = remaining[current];
            boolean wasOpened = opened[current];

            boolean attempt = wasOpened && OutRules.isOneDartFinish(before, config.outRule());
            if (attempt) ps.checkoutAttempts++;

            int counted;
            if (!wasOpened) {
                if (dart.isDouble()) {
                    opened[current] = true;
                    counted = dart.points();
                } else {
                    counted = 0;
                }
            } else {
                counted = dart.points();
            }

            int after = before - counted;
            boolean bust = after < 0
                    || OutRules.isDeadRemainder(after, config.outRule())
                    || (after == 0 && !OutRules.isFinishingDart(dart, config.outRule()));

            ps.dartsThrown++;
            boolean inFirst9 = turn.turnInLeg <= 3;
            if (inFirst9) ps.first9Darts++;

            if (bust) {
                turn.darts.add(new DartResult(dart, counted, turn.startRemaining, attempt));
                turn.bust = true;
                remaining[current] = turn.startRemaining;
                // An opening double thrown in a busted turn does not stand.
                if (config.inRule() == InRule.DOUBLE && turn.startRemaining == config.startScore()) {
                    opened[current] = false;
                }
                // Points of the busted turn are discarded from the first-9 tally as well.
                if (inFirst9) ps.first9Points -= turn.darts.stream().limit(turn.darts.size() - 1).mapToInt(DartResult::counted).sum();
                ps.pointsScored -= turn.darts.stream().limit(turn.darts.size() - 1).mapToInt(DartResult::counted).sum();
                endTurn(EventType.BUST);
                return;
            }

            turn.darts.add(new DartResult(dart, counted, after, attempt));
            remaining[current] = after;
            ps.pointsScored += counted;
            if (inFirst9) ps.first9Points += counted;

            if (after == 0) {
                turn.checkout = true;
                ps.checkoutsHit++;
                ps.highestCheckout = Math.max(ps.highestCheckout, turn.scored());
                Leg legRef = legs.getLast();
                legRef.winnerIndex = current;
                legsWon[current]++;
                ps.legsWon++;
                recordTurnScore(turn);
                lastCompleted = turn;
                if (legsWon[current] >= config.legsToWin()) {
                    winner = current;
                    turn = null;
                    lastEvent = EventType.MATCH_WON;
                } else {
                    startLeg();
                    lastEvent = EventType.LEG_WON;
                }
                return;
            }

            if (turn.darts.size() == DARTS_PER_TURN) {
                endTurn(EventType.DART);
            } else {
                lastEvent = EventType.DART;
            }
        }

        private void endTurn(EventType event) {
            recordTurnScore(turn);
            lastCompleted = turn;
            turn = null;
            current = 1 - current;
            lastEvent = event;
        }

        private void recordTurnScore(Turn t) {
            PlayerStats ps = stats[t.playerIndex];
            int scored = t.scored();
            ps.highestTurn = Math.max(ps.highestTurn, scored);
            if (scored == 180) ps.count180++;
            else if (scored >= 140) ps.count140++;
            else if (scored >= 100) ps.count100++;
        }

        MatchState finish(int dartCount) {
            return new MatchState(config, startingPlayer, legs, legsWon.clone(), remaining.clone(), opened.clone(),
                    winner == null ? current : winner, turn, lastCompleted, winner, stats, lastEvent, dartCount);
        }
    }
}
