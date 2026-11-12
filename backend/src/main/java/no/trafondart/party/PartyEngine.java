package no.trafondart.party;

import no.trafondart.game.CheckoutCalculator;
import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.OutRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Replays the darts of a party game into its current state. Pure and deterministic, like the X01 engine for 1v1
 * matches: the darts are the only truth, so undo is "drop the last dart and replay".
 *
 * <p>Players throw three darts each in slot order. In doubles, slots alternate between the two teams (A1, B1, A2, B2)
 * and team mates share one score. A new leg starts with the next player in order.
 */
public final class PartyEngine {

    private PartyEngine() {
    }

    public static PartyState replay(GameType type, PartySettings settings, List<Integer> slotSides, List<Dart> darts) {
        int sides = checkSides(slotSides);
        Sim sim = switch (type) {
            case X01 -> new X01(settings, slotSides, sides);
            case CRICKET -> new Cricket(settings, slotSides, sides);
            case KILLER -> new Killer(settings, slotSides, sides);
            case AROUND_THE_CLOCK -> new Clock(settings, slotSides, sides);
        };
        for (Dart d : darts) sim.apply(d);
        return sim.state(type);
    }

    /** Sides are 0..k-1. Either everyone plays alone, or there are two teams of two in alternating order. */
    private static int checkSides(List<Integer> slotSides) {
        if (slotSides == null || slotSides.isEmpty() || slotSides.size() > 8) throw new IllegalArgumentException("players");
        int n = slotSides.size();
        int k = (int) slotSides.stream().distinct().count();
        if (k == n) {
            for (int i = 0; i < n; i++) if (slotSides.get(i) != i) throw new IllegalArgumentException("sides must be 0..n-1 in order");
        } else {
            if (n != 4 || k != 2) throw new IllegalArgumentException("teams are two pairs");
            for (int i = 0; i < n; i++) if (slotSides.get(i) != i % 2) throw new IllegalArgumentException("team mates must alternate");
        }
        return k;
    }

    // ------------------------------------------------------------------ shared turn mechanics

    private enum After { CONTINUE, END_TURN, HANDLED }

    private abstract static class Sim {
        final PartySettings s;
        final int n;
        final int k;
        final int[] sideOf;
        final int[] legsWon;
        final int[] dartsBy;
        final int[] scoreBy;
        final int[] count180;
        final int[] highestCheckout;
        int[] turnsThisLeg;
        int leg = 1;
        int legStarter;
        int current;
        int dartCount;
        boolean finished;
        Integer winner;
        Dart last;
        final List<Dart> turn = new ArrayList<>();

        Sim(PartySettings s, List<Integer> slotSides, int k) {
            this.s = s;
            this.n = slotSides.size();
            this.k = k;
            this.sideOf = slotSides.stream().mapToInt(Integer::intValue).toArray();
            this.legsWon = new int[k];
            this.dartsBy = new int[n];
            this.scoreBy = new int[n];
            this.count180 = new int[n];
            this.highestCheckout = new int[n];
            this.turnsThisLeg = new int[n];
        }

        final void apply(Dart d) {
            if (finished) throw new IllegalStateException("The game is over");
            if (turn.isEmpty()) onTurnStart();
            dartCount++;
            last = d;
            dartsBy[current]++;
            turn.add(d);
            After after = onDart(d);
            if (after == After.HANDLED) return;
            if (after == After.END_TURN || turn.size() == 3) endTurn();
        }

        private void endTurn() {
            onTurnEnd();
            turnsThisLeg[current]++;
            turn.clear();
            int legBefore = leg;
            afterTurn();
            if (!finished && leg == legBefore) current = next(current);
        }

        int next(int from) {
            for (int i = 1; i <= n; i++) {
                int c = (from + i) % n;
                if (active(c)) return c;
            }
            return from;
        }

        boolean active(int slot) {
            return true;
        }

        /** A side won the leg: maybe the game, otherwise start the next leg with the next player. */
        After legWon(int side) {
            legsWon[side]++;
            if (legsWon[side] >= legsToWin()) {
                finished = true;
                winner = side;
                turn.clear();
                return After.HANDLED;
            }
            leg++;
            legStarter = (legStarter + 1) % n;
            current = legStarter;
            turn.clear();
            turnsThisLeg = new int[n];
            newLeg();
            return After.HANDLED;
        }

        int legsToWin() {
            return s.legsToWin() == null ? 1 : s.legsToWin();
        }

        void onTurnStart() {
        }

        abstract After onDart(Dart d);

        void onTurnEnd() {
        }

        void afterTurn() {
        }

        void newLeg() {
        }

        abstract PartyState.SideState side(int side);

        /** Best first. Only called when the game is over; the winner must come first. */
        abstract Comparator<Integer> ranking();

        List<Dart> checkout() {
            return List.of();
        }

        final PartyState state(GameType type) {
            List<PartyState.SideState> sideStates = IntStream.range(0, k).mapToObj(this::side).toList();
            int[] placeOfSide = new int[k];
            if (finished) {
                List<Integer> order = IntStream.range(0, k).boxed().sorted(
                        Comparator.<Integer, Boolean>comparing(x -> !x.equals(winner)).thenComparing(ranking()).thenComparing(x -> x)).toList();
                for (int i = 0; i < order.size(); i++) placeOfSide[order.get(i)] = i + 1;
            }
            List<PartyState.SlotStats> slots = IntStream.range(0, n).mapToObj(i -> new PartyState.SlotStats(i, sideOf[i], dartsBy[i],
                    scoreBy[i], count180[i], highestCheckout[i], finished ? placeOfSide[sideOf[i]] : 0)).toList();
            return new PartyState(type, dartCount, current, leg, turnsThisLeg[current] + 1, List.copyOf(turn),
                    finished ? List.of() : checkout(), finished, winner, last, sideStates, slots);
        }
    }

    // ------------------------------------------------------------------ X01

    private static final class X01 extends Sim {
        final int[] remaining;
        final boolean[] opened;
        int turnStart;

        X01(PartySettings s, List<Integer> slotSides, int k) {
            super(s, slotSides, k);
            remaining = new int[k];
            opened = new boolean[k];
            newLeg();
        }

        @Override
        void newLeg() {
            for (int i = 0; i < k; i++) {
                remaining[i] = s.startScore();
                opened[i] = s.inRule() != InRule.DOUBLE;
            }
        }

        @Override
        void onTurnStart() {
            turnStart = remaining[sideOf[current]];
        }

        @Override
        After onDart(Dart d) {
            int side = sideOf[current];
            if (!opened[side]) {
                if (!d.isDouble()) return After.CONTINUE;
                opened[side] = true;
            }
            int left = remaining[side] - d.points();
            boolean bust = left < 0 || (left == 0 && !OutRules.isFinishingDart(d, s.outRule()))
                    || OutRules.isDeadRemainder(left, s.outRule());
            if (bust) {
                remaining[side] = turnStart;
                return After.END_TURN; // scored nothing this turn
            }
            remaining[side] = left;
            if (left == 0) {
                scoreBy[current] += turnStart;
                highestCheckout[current] = Math.max(highestCheckout[current], turnStart);
                if (turnStart == 180) count180[current]++;
                return legWon(side);
            }
            return After.CONTINUE;
        }

        @Override
        void onTurnEnd() {
            int scored = turnStart - remaining[sideOf[current]];
            scoreBy[current] += scored;
            if (scored == 180) count180[current]++;
        }

        @Override
        List<Dart> checkout() {
            int side = sideOf[current];
            if (!opened[side]) return List.of();
            return CheckoutCalculator.suggest(remaining[side], 3 - turn.size(), s.outRule()).orElse(List.of());
        }

        @Override
        PartyState.SideState side(int side) {
            return new PartyState.SideState(side, legsWon[side], remaining[side], opened[side], null, null, null, null, null, null, null, null);
        }

        @Override
        Comparator<Integer> ranking() {
            return Comparator.<Integer>comparingInt(x -> -legsWon[x]).thenComparingInt(x -> remaining[x]);
        }
    }

    // ------------------------------------------------------------------ Cricket

    private static final class Cricket extends Sim {
        static final int[] TARGETS = {15, 16, 17, 18, 19, 20, 25};
        final int[][] marks;
        final int[] points;
        final int[] totalMarks;

        Cricket(PartySettings s, List<Integer> slotSides, int k) {
            super(s, slotSides, k);
            marks = new int[k][TARGETS.length];
            points = new int[k];
            totalMarks = new int[k];
        }

        static int index(int segment) {
            if (segment == 25) return 6;
            return segment >= 15 && segment <= 20 ? segment - 15 : -1;
        }

        @Override
        void newLeg() {
            for (int i = 0; i < k; i++) {
                marks[i] = new int[TARGETS.length];
                points[i] = 0;
            }
        }

        @Override
        After onDart(Dart d) {
            int idx = d.isMiss() ? -1 : index(d.segment());
            if (idx < 0) return After.CONTINUE;
            int side = sideOf[current];
            for (int m = 0; m < d.multiplier(); m++) {
                scoreBy[current]++;
                totalMarks[side]++;
                if (marks[side][idx] < 3) {
                    marks[side][idx]++;
                } else if (someoneOpen(side, idx)) {
                    points[side] += TARGETS[idx];
                }
            }
            if (closedAll(side) && points[side] >= maxOtherPoints(side)) return legWon(side);
            return After.CONTINUE;
        }

        boolean someoneOpen(int side, int idx) {
            for (int o = 0; o < k; o++) if (o != side && marks[o][idx] < 3) return true;
            return false;
        }

        boolean closedAll(int side) {
            for (int m : marks[side]) if (m < 3) return false;
            return true;
        }

        int maxOtherPoints(int side) {
            int max = 0;
            for (int o = 0; o < k; o++) if (o != side) max = Math.max(max, points[o]);
            return max;
        }

        @Override
        void afterTurn() {
            int limit = s.roundLimit() == null ? 0 : s.roundLimit();
            if (limit <= 0) return;
            for (int t : turnsThisLeg) if (t < limit) return;
            // Out of rounds: most points wins the leg, then most marks, then first in order.
            int best = 0;
            for (int side = 1; side < k; side++) {
                if (points[side] > points[best] || (points[side] == points[best] && legMarks(side) > legMarks(best))) best = side;
            }
            legWon(best);
        }

        int legMarks(int side) {
            int sum = 0;
            for (int m : marks[side]) sum += m;
            return sum;
        }

        @Override
        PartyState.SideState side(int side) {
            Map<Integer, Integer> m = new LinkedHashMap<>();
            for (int i = 0; i < TARGETS.length; i++) m.put(TARGETS[i], marks[side][i]);
            return new PartyState.SideState(side, legsWon[side], null, null, m, points[side], null, null, null, null, null, null);
        }

        @Override
        Comparator<Integer> ranking() {
            return Comparator.<Integer>comparingInt(x -> -legsWon[x]).thenComparingInt(x -> -points[x]).thenComparingInt(x -> -totalMarks[x]);
        }
    }

    // ------------------------------------------------------------------ Killer

    private static final class Killer extends Sim {
        final int[] lives;
        final boolean[] killer;
        final List<Integer> eliminated = new ArrayList<>();

        Killer(PartySettings s, List<Integer> slotSides, int k) {
            super(s, slotSides, k);
            lives = new int[n];
            killer = new boolean[n];
            java.util.Arrays.fill(lives, s.lives());
        }

        @Override
        boolean active(int slot) {
            return lives[slot] > 0;
        }

        @Override
        After onDart(Dart d) {
            if (!d.isDouble() || d.segment() == 25) return After.CONTINUE;
            int owner = s.killerNumbers().indexOf(d.segment());
            if (owner < 0 || lives[owner] == 0) return After.CONTINUE;
            if (owner == current) {
                killer[current] = true;
                return After.CONTINUE;
            }
            if (!killer[current]) return After.CONTINUE;
            lives[owner]--;
            scoreBy[current]++;
            if (lives[owner] == 0) {
                eliminated.add(owner);
                if (eliminated.size() == n - 1) {
                    finished = true;
                    winner = current;
                    turn.clear();
                    return After.HANDLED;
                }
            }
            return After.CONTINUE;
        }

        @Override
        PartyState.SideState side(int side) {
            return new PartyState.SideState(side, 0, null, null, null, null, s.killerNumbers().get(side), lives[side], killer[side],
                    lives[side] == 0, null, null);
        }

        @Override
        Comparator<Integer> ranking() {
            // knocked out later = better; nobody else is left standing when the game ends
            return Comparator.comparingInt(x -> -eliminated.indexOf(x));
        }
    }

    // ------------------------------------------------------------------ Around the Clock

    private static final class Clock extends Sim {
        /** 0..19 means "hit index+1 next", 20 means the bull. */
        final int[] position;
        final boolean[] done;

        Clock(PartySettings s, List<Integer> slotSides, int k) {
            super(s, slotSides, k);
            position = new int[n];
            done = new boolean[n];
        }

        @Override
        After onDart(Dart d) {
            int p = position[current];
            if (p == 20) {
                if (d.segment() != 25) return After.CONTINUE;
                done[current] = true;
                scoreBy[current] = 21;
                finished = true;
                winner = sideOf[current];
                turn.clear();
                return After.HANDLED;
            }
            if (d.segment() != p + 1) return After.CONTINUE;
            int step = Boolean.TRUE.equals(s.multiplierSkips()) ? d.multiplier() : 1;
            position[current] = Math.min(20, p + step);
            scoreBy[current] = position[current];
            return After.CONTINUE;
        }

        @Override
        PartyState.SideState side(int side) {
            int p = position[side];
            return new PartyState.SideState(side, 0, null, null, null, null, null, null, null, null, p == 20 ? 25 : p + 1, done[side]);
        }

        @Override
        Comparator<Integer> ranking() {
            return Comparator.<Integer>comparingInt(x -> done[x] ? -100 : -position[x]).thenComparingInt(x -> dartsBy[x]);
        }
    }
}
