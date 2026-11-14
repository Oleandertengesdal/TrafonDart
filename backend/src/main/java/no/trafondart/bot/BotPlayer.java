package no.trafondart.bot;

import no.trafondart.game.CheckoutCalculator;
import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig.OutRule;
import no.trafondart.game.X01Engine;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Trafo, the practice bot. It plays like a person: it aims where a player would (treble 20 to score, a set-up shot,
 * then the double from the checkout table), and its hand is unsteady – each dart lands somewhere around the aim point,
 * spread by the level's {@link BotLevel#sigma()}. So a miss lands in a neighbouring bed, as it would for you.
 *
 * <p>Every dart is drawn from a random generator seeded with the match's seed and the dart's number, so a match replays
 * the same way (and an undo can't be used to re-roll the bot's darts).
 */
public final class BotPlayer {

    private BotPlayer() {
    }

    /** The bot's next dart in a match, where it is the player at {@code botIndex} and it's its turn. */
    public static Dart nextDart(X01Engine.MatchState state, int botIndex, BotLevel level, long seed) {
        if (state.finished() || state.currentPlayerIndex() != botIndex) {
            throw new IllegalStateException("It is not the bot's turn");
        }
        Dart target = aim(state.remaining()[botIndex], state.dartsLeftInTurn(), state.opened()[botIndex], state.config().outRule());
        return throwAt(target, level.sigma(), new SplittableRandom(seed * 0x9E3779B97F4A7C15L + state.dartCount()));
    }

    /**
     * Where a player aims: a double to get in (double in); the first dart of the checkout route when the score can be
     * finished with the darts left; otherwise the first dart of the three-dart route, which sets up a finish; and with
     * no finish in reach, treble 20.
     */
    public static Dart aim(int remaining, int dartsLeft, boolean opened, OutRule out) {
        if (!opened) return Dart.dbl(20);
        List<Dart> route = CheckoutCalculator.suggest(remaining, dartsLeft, out)
                .or(() -> CheckoutCalculator.suggest(remaining, CheckoutCalculator.MAX_DARTS, out))
                .orElse(null);
        return route == null ? Dart.treble(20) : route.getFirst();
    }

    /** One dart thrown at {@code target} by a hand with the given spread (mm). */
    public static Dart throwAt(Dart target, double sigma, RandomGenerator rng) {
        double[] p = Dartboard.aimPoint(target);
        return Dartboard.hit(p[0] + rng.nextGaussian() * sigma, p[1] + rng.nextGaussian() * sigma);
    }
}
