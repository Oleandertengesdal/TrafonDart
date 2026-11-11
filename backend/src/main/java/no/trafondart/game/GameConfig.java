package no.trafondart.game;

import java.util.Set;

/** Rules for one X01 match. */
public record GameConfig(int startScore, InRule inRule, OutRule outRule, int legsToWin) {

    public static final Set<Integer> ALLOWED_START_SCORES = Set.of(301, 501, 701);
    public static final int MAX_LEGS_TO_WIN = 11;

    public GameConfig {
        if (!ALLOWED_START_SCORES.contains(startScore)) {
            throw new IllegalArgumentException("Start score must be 301, 501 or 701");
        }
        if (inRule == null || outRule == null) {
            throw new IllegalArgumentException("In- and out-rule are required");
        }
        if (legsToWin < 1 || legsToWin > MAX_LEGS_TO_WIN) {
            throw new IllegalArgumentException("Legs to win must be between 1 and " + MAX_LEGS_TO_WIN);
        }
    }

    public enum InRule { STRAIGHT, DOUBLE }

    public enum OutRule { STRAIGHT, DOUBLE, MASTER }
}
