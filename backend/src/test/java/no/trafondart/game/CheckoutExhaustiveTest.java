package no.trafondart.game;

import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brute force over every possible one-, two- and three-dart combination: the precomputed checkout table must
 * never miss a finish, never suggest a longer route than needed, and its routes must be accepted by the engine.
 */
class CheckoutExhaustiveTest {

    private static final List<Dart> SCORING = scoringDarts();

    private static List<Dart> scoringDarts() {
        List<Dart> all = new ArrayList<>();
        all.add(Dart.OUTER_BULL);
        all.add(Dart.BULL);
        for (int n = 1; n <= 20; n++) for (int m = 1; m <= 3; m++) all.add(new Dart(n, m));
        return all;
    }

    /** Fewest darts that finish {@code score} under {@code rule}, or 0 if impossible within {@code maxDarts}. */
    private static int fewestDarts(int score, int maxDarts, OutRule rule) {
        for (Dart last : SCORING) {
            if (!OutRules.isFinishingDart(last, rule)) continue;
            if (last.points() == score) return 1;
        }
        if (maxDarts >= 2) {
            for (Dart a : SCORING) for (Dart last : SCORING) {
                if (OutRules.isFinishingDart(last, rule) && a.points() + last.points() == score) return 2;
            }
        }
        if (maxDarts >= 3) {
            for (Dart a : SCORING) for (Dart b : SCORING) for (Dart last : SCORING) {
                if (OutRules.isFinishingDart(last, rule) && a.points() + b.points() + last.points() == score) return 3;
            }
        }
        return 0;
    }

    @Test
    void tableIsCompleteAndMinimalForEveryScoreRuleAndDartCount() {
        for (OutRule rule : OutRule.values()) {
            for (int darts = 1; darts <= 3; darts++) {
                for (int score = 1; score <= 180; score++) {
                    int best = fewestDarts(score, darts, rule);
                    Optional<List<Dart>> route = CheckoutCalculator.suggest(score, darts, rule);
                    if (best == 0) {
                        assertThat(route).as("%s %d with %d darts should be impossible", rule, score, darts).isEmpty();
                    } else {
                        assertThat(route).as("%s %d with %d darts has a finish", rule, score, darts).isPresent();
                        assertThat(route.get()).as("%s %d route %s is minimal", rule, score, route.get()).hasSize(best);
                    }
                }
            }
        }
    }

    @Test
    void theEngineAcceptsEverySuggestedRouteAsALegWin() {
        for (OutRule rule : OutRule.values()) {
            for (int score = 2; score <= 170; score++) {
                Optional<List<Dart>> route = CheckoutCalculator.suggest(score, 3, rule);
                if (route.isEmpty()) continue;
                // Bring player 0 down to `score` on 501 with a fixed prelude, opponent misses.
                List<Dart> darts = new ArrayList<>();
                int left = 501;
                while (left - score >= 60) {
                    darts.addAll(List.of(Dart.treble(20), Dart.MISS, Dart.MISS, Dart.MISS, Dart.MISS, Dart.MISS));
                    left -= 60;
                }
                int gap = left - score; // 0..59, thrown as up to three singles
                if (gap > 0) {
                    List<Dart> turn = new ArrayList<>();
                    while (gap > 0) {
                        int n = Math.min(20, gap);
                        turn.add(Dart.single(n));
                        gap -= n;
                    }
                    while (turn.size() < 3) turn.add(Dart.MISS);
                    darts.addAll(turn);
                    darts.addAll(List.of(Dart.MISS, Dart.MISS, Dart.MISS));
                }
                GameConfig cfg = new GameConfig(501, InRule.STRAIGHT, rule, 1);
                X01Engine.MatchState before = X01Engine.replay(cfg, 0, darts);
                assertThat(before.remaining()[0]).isEqualTo(score);
                assertThat(before.currentPlayerIndex()).isZero();
                darts.addAll(route.get());
                X01Engine.MatchState after = X01Engine.replay(cfg, 0, darts);
                assertThat(after.winnerIndex()).as("%s route %s for %d", rule, route.get(), score).isEqualTo(0);
            }
        }
    }

    @Test
    void outOfRangeInputsGiveNoRoute() {
        assertThat(CheckoutCalculator.suggest(0, 3, OutRule.DOUBLE)).isEmpty();
        assertThat(CheckoutCalculator.suggest(-5, 3, OutRule.DOUBLE)).isEmpty();
        assertThat(CheckoutCalculator.suggest(181, 3, OutRule.STRAIGHT)).isEmpty();
        assertThat(CheckoutCalculator.suggest(40, 0, OutRule.DOUBLE)).isEmpty();
        assertThat(CheckoutCalculator.suggest(40, 4, OutRule.DOUBLE)).isEmpty();
    }
}
