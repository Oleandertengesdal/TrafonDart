package no.trafondart.game;

import no.trafondart.game.GameConfig.OutRule;

/** The finishing rules shared by the engine and the checkout calculator. */
public final class OutRules {

    private OutRules() {
    }

    /** May a leg be finished (remaining reaches exactly zero) with this dart? */
    public static boolean isFinishingDart(Dart dart, OutRule rule) {
        if (dart.isMiss()) return false;
        return switch (rule) {
            case STRAIGHT -> true;
            case DOUBLE -> dart.isDouble();
            case MASTER -> dart.isDouble() || dart.isTreble();
        };
    }

    /** A remainder that can never be finished under the rule; landing on it is a bust. */
    public static boolean isDeadRemainder(int remaining, OutRule rule) {
        return remaining == 1 && rule != OutRule.STRAIGHT;
    }

    /** Can {@code remaining} be checked out with a single dart? Used for checkout-attempt statistics. */
    public static boolean isOneDartFinish(int remaining, OutRule rule) {
        if (remaining <= 0) return false;
        boolean isDoubleValue = remaining == 50 || (remaining <= 40 && remaining % 2 == 0);
        boolean isTrebleValue = remaining <= 60 && remaining % 3 == 0;
        boolean isSingleValue = remaining <= 20 || remaining == 25;
        return switch (rule) {
            case DOUBLE -> isDoubleValue;
            case MASTER -> isDoubleValue || isTrebleValue;
            case STRAIGHT -> isDoubleValue || isTrebleValue || isSingleValue;
        };
    }
}
