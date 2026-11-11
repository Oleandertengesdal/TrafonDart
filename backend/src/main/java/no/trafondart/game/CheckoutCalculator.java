package no.trafondart.game;

import no.trafondart.game.GameConfig.OutRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Suggests the best checkout route for a remaining score.
 *
 * <p>All routes for 1–3 darts are enumerated once at start-up and ranked; the best route per
 * (out-rule, darts left, remaining) is kept in a lookup table, so a suggestion is an O(1) array read.
 *
 * <p>Ranking (lower cost wins):
 * <ol>
 *   <li>fewer darts</li>
 *   <li>a preferred finishing double (D20, D16, D8, … – doubles that split well when missed)</li>
 *   <li>easy set-up darts: T20/T19 and big singles are cheap, small trebles and doubles are expensive</li>
 *   <li>higher-scoring darts first</li>
 * </ol>
 */
public final class CheckoutCalculator {

    public static final int MAX_DARTS = 3;
    private static final int MAX_SCORE = 180;

    /** Finishing-double preference, best first. */
    private static final int[] DOUBLE_PREFERENCE = {20, 16, 8, 18, 12, 10, 14, 4, 6, 2, 19, 17, 15, 13, 11, 9, 7, 5, 3, 1};

    private static final List<Dart> ALL_DARTS = buildAllDarts();
    private static final Map<OutRule, List<Dart>[][]> TABLE = buildTable();

    private CheckoutCalculator() {
    }

    /**
     * @param remaining the player's remaining score
     * @param dartsLeft darts left in the current turn (1–3)
     * @param rule      the match's out-rule
     * @return the suggested route, or empty if the score cannot be finished with the darts left
     */
    public static Optional<List<Dart>> suggest(int remaining, int dartsLeft, OutRule rule) {
        if (remaining < 1 || remaining > MAX_SCORE || dartsLeft < 1 || dartsLeft > MAX_DARTS) {
            return Optional.empty();
        }
        return Optional.ofNullable(TABLE.get(rule)[dartsLeft][remaining]);
    }

    /** True if the score has no checkout with three darts (e.g. 169, 168, 166, 165, 163, 162, 159 under double out). */
    public static boolean isBogey(int remaining, OutRule rule) {
        return suggest(remaining, MAX_DARTS, rule).isEmpty();
    }

    private static List<Dart> buildAllDarts() {
        List<Dart> darts = new ArrayList<>();
        for (int n = 20; n >= 1; n--) {
            darts.add(Dart.treble(n));
        }
        darts.add(Dart.BULL);
        for (int n = 20; n >= 1; n--) {
            darts.add(Dart.dbl(n));
        }
        darts.add(Dart.OUTER_BULL);
        for (int n = 20; n >= 1; n--) {
            darts.add(Dart.single(n));
        }
        return Collections.unmodifiableList(darts);
    }

    @SuppressWarnings("unchecked")
    private static Map<OutRule, List<Dart>[][]> buildTable() {
        Map<OutRule, List<Dart>[][]> table = new EnumMap<>(OutRule.class);
        for (OutRule rule : OutRule.values()) {
            List<Dart>[][] best = new List[MAX_DARTS + 1][MAX_SCORE + 1];
            double[][] bestCost = new double[MAX_DARTS + 1][MAX_SCORE + 1];
            for (double[] row : bestCost) java.util.Arrays.fill(row, Double.MAX_VALUE);

            for (Dart finish : ALL_DARTS) {
                if (!OutRules.isFinishingDart(finish, rule)) continue;
                consider(List.of(finish), rule, best, bestCost);
                for (Dart a : ALL_DARTS) {
                    consider(List.of(a, finish), rule, best, bestCost);
                    for (Dart b : ALL_DARTS) {
                        consider(List.of(a, b, finish), rule, best, bestCost);
                    }
                }
            }
            // A route that needs fewer darts is also valid when more darts are left.
            for (int darts = 2; darts <= MAX_DARTS; darts++) {
                for (int score = 1; score <= MAX_SCORE; score++) {
                    if (best[darts - 1][score] != null && bestCost[darts - 1][score] <= bestCost[darts][score]) {
                        best[darts][score] = best[darts - 1][score];
                        bestCost[darts][score] = bestCost[darts - 1][score];
                    }
                }
            }
            table.put(rule, best);
        }
        return table;
    }

    private static void consider(List<Dart> route, OutRule rule, List<Dart>[][] best, double[][] bestCost) {
        int total = route.stream().mapToInt(Dart::points).sum();
        if (total > MAX_SCORE) return;
        // Set-up darts must not leave a dead or negative remainder before the finishing dart.
        int running = total;
        for (int i = 0; i < route.size() - 1; i++) {
            running -= route.get(i).points();
            if (running <= 0 || OutRules.isDeadRemainder(running, rule)) return;
        }
        double cost = cost(route, rule);
        int darts = route.size();
        if (cost < bestCost[darts][total]) {
            bestCost[darts][total] = cost;
            best[darts][total] = List.copyOf(route);
        }
    }

    static double cost(List<Dart> route, OutRule rule) {
        double cost = 1000.0 * route.size();
        Dart finish = route.getLast();
        cost += finishCost(finish, rule);
        for (int i = 0; i < route.size() - 1; i++) {
            cost += setupCost(route.get(i));
            // Prefer the bigger set-up dart first.
            if (i > 0 && route.get(i).points() > route.get(i - 1).points()) cost += 0.5;
        }
        return cost;
    }

    private static double finishCost(Dart dart, OutRule rule) {
        if (rule == OutRule.STRAIGHT) {
            if (dart.multiplier() == 1 && dart.segment() <= 20) return 0;
            if (dart.segment() == 25) return dart.isDouble() ? 8 : 6;
            return 4;
        }
        if (dart.segment() == 25) return 80; // bullseye finish: possible but a small target
        if (dart.isDouble()) {
            for (int i = 0; i < DOUBLE_PREFERENCE.length; i++) {
                if (DOUBLE_PREFERENCE[i] == dart.segment()) return i * 10.0;
            }
        }
        // treble finish under master out
        return 25 + (20 - dart.segment());
    }

    private static double setupCost(Dart dart) {
        if (dart.isTreble()) {
            return switch (dart.segment()) {
                case 20 -> 0;
                case 19 -> 2;
                case 18 -> 4;
                case 17 -> 6;
                case 16 -> 7;
                case 15 -> 8;
                default -> dart.segment() >= 10 ? 20 : 30;
            };
        }
        if (dart.segment() == 25) return dart.isDouble() ? 15 : 6;
        if (dart.isDouble()) return 12 + (20 - dart.segment()) * 0.1;
        return 1 + (20 - dart.segment()) * 0.05; // singles: big, forgiving target
    }
}
