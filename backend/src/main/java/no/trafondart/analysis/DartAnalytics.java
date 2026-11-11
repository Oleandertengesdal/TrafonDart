package no.trafondart.analysis;

import no.trafondart.game.Dart;
import no.trafondart.game.X01Engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure aggregation of replayed matches into "nerdy" darts statistics: where darts land, how turns are
 * distributed, hold/break, best legs and favourite finishing doubles. No Spring, no database – fed by
 * {@link AnalyticsService} and unit-tested on its own.
 */
public final class DartAnalytics {

    /** Turn-score bands, lower bound inclusive. The last band is exactly 180. */
    public static final int[] BAND_LOWER = {0, 20, 40, 60, 80, 100, 140, 180};
    public static final String[] BAND_LABEL = {"0–19", "20–39", "40–59", "60–79", "80–99", "100–139", "140–179", "180"};

    public record HeatCell(int segment, int multiplier, int count) {
    }

    public record Band(String label, int count) {
    }

    public record LabelCount(String label, int count) {
    }

    public record Rate(int won, int of) {
    }

    /** Accumulates statistics for one player over any number of legs/matches. */
    public static final class Accumulator {
        private final Map<Long, Integer> heat = new HashMap<>(); // key = segment * 10 + multiplier
        private final int[] bands = new int[BAND_LOWER.length];
        private final Map<String, Integer> finishingDarts = new HashMap<>();
        private final Map<Integer, Integer> turnScores = new HashMap<>();
        private int darts;
        private int points;
        private int trebles;
        private int doubles;
        private int bullHits;
        private int misses;
        private int turns;
        private int busts;
        private int legsPlayed;
        private int legsWon;
        private int holdWon;
        private int holdOf;
        private int breakWon;
        private int breakOf;
        private int bestLeg = Integer.MAX_VALUE;
        private int wonLegDarts;

        /** Adds every turn {@code playerIndex} threw in {@code state}. */
        public void addMatch(X01Engine.MatchState state, int playerIndex) {
            for (X01Engine.Leg leg : state.legs()) {
                boolean finished = leg.winnerIndex() != null;
                for (X01Engine.Turn t : leg.turns()) {
                    if (t.playerIndex() != playerIndex) continue;
                    addTurn(t);
                }
                if (!finished) continue;
                legsPlayed++;
                boolean won = leg.winnerIndex() == playerIndex;
                boolean started = leg.starterIndex() == playerIndex;
                if (started) {
                    holdOf++;
                    if (won) holdWon++;
                } else {
                    breakOf++;
                    if (won) breakWon++;
                }
                if (won) {
                    legsWon++;
                    int used = leg.dartsThrownBy(playerIndex);
                    wonLegDarts += used;
                    bestLeg = Math.min(bestLeg, used);
                    X01Engine.Turn last = leg.turns().getLast();
                    Dart finisher = last.darts().getLast().dart();
                    finishingDarts.merge(finisher.label(), 1, Integer::sum);
                }
            }
        }

        private void addTurn(X01Engine.Turn t) {
            for (X01Engine.DartResult r : t.darts()) {
                Dart d = r.dart();
                darts++;
                heat.merge((long) d.segment() * 10 + d.multiplier(), 1, Integer::sum);
                if (d.isMiss()) misses++;
                else if (d.isTreble()) trebles++;
                else if (d.isDouble()) doubles++;
                if (d.segment() == 25) bullHits++;
            }
            if (!t.complete()) return; // the live turn in progress
            turns++;
            if (t.bust()) {
                busts++;
                return;
            }
            int scored = t.scored();
            points += scored;
            turnScores.merge(scored, 1, Integer::sum);
            for (int i = BAND_LOWER.length - 1; i >= 0; i--) {
                if (scored >= BAND_LOWER[i]) {
                    bands[i]++;
                    break;
                }
            }
        }

        public int darts() { return darts; }
        public int points() { return points; }
        public int turns() { return turns; }
        public int busts() { return busts; }
        public int legsPlayed() { return legsPlayed; }
        public int legsWon() { return legsWon; }

        public double rate(int count) {
            return darts == 0 ? 0 : Math.round(count * 1000.0 / darts) / 10.0;
        }

        public double trebleRate() { return rate(trebles); }
        public double doubleRate() { return rate(doubles); }
        public double bullRate() { return rate(bullHits); }
        public double missRate() { return rate(misses); }
        public double bustRate() { return turns == 0 ? 0 : Math.round(busts * 1000.0 / turns) / 10.0; }

        public Integer bestLeg() { return bestLeg == Integer.MAX_VALUE ? null : bestLeg; }

        public double avgDartsPerLegWon() {
            return legsWon == 0 ? 0 : Math.round(wonLegDarts * 10.0 / legsWon) / 10.0;
        }

        public Rate hold() { return new Rate(holdWon, holdOf); }
        public Rate breaks() { return new Rate(breakWon, breakOf); }

        public List<HeatCell> heatmap() {
            List<HeatCell> cells = new ArrayList<>();
            heat.forEach((k, v) -> cells.add(new HeatCell((int) (k / 10), (int) (k % 10), v)));
            cells.sort(Comparator.comparingInt(HeatCell::segment).thenComparingInt(HeatCell::multiplier));
            return cells;
        }

        public List<Band> bands() {
            List<Band> out = new ArrayList<>();
            for (int i = 0; i < BAND_LOWER.length; i++) out.add(new Band(BAND_LABEL[i], bands[i]));
            return out;
        }

        /** Finishing darts, most used first (top {@code limit}). */
        public List<LabelCount> finishes(int limit) {
            return finishingDarts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(limit)
                    .map(e -> new LabelCount(e.getKey(), e.getValue()))
                    .toList();
        }

        /** The turn score thrown most often (ties → higher score), or null. */
        public Integer mostCommonTurn() {
            return turnScores.entrySet().stream()
                    .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).orElse(null);
        }

        /** Most-hit board segment (1–20 or 25), ignoring misses. */
        public Integer favouriteSegment() {
            Map<Integer, Integer> bySegment = new LinkedHashMap<>();
            heat.forEach((k, v) -> {
                int seg = (int) (k / 10);
                if (seg != 0) bySegment.merge(seg, v, Integer::sum);
            });
            return bySegment.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        }
    }

    private DartAnalytics() {
    }
}
