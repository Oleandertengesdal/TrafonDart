package no.trafondart.tournament;

import java.util.ArrayList;
import java.util.List;

/** Pure fixture generation for both tournament formats. Players are identified by user id. */
public final class BracketGenerator {

    private BracketGenerator() {
    }

    /** A generated fixture. {@code null} players mean "to be decided" (knockout) – byes are explicit via {@code bye}. */
    public record Slot(int round, int position, Long player1, Long player2, boolean bye) {
    }

    /**
     * Round-robin with the circle method: every player meets every other player exactly once.
     * With an odd number of players one player sits out each round (no fixture is generated for that pairing).
     */
    public static List<Slot> roundRobin(List<Long> players) {
        if (players.size() < 2) throw new IllegalArgumentException("At least two players are required");
        List<Long> ring = new ArrayList<>(players);
        if (ring.size() % 2 == 1) ring.add(null);
        int n = ring.size();
        List<Slot> fixtures = new ArrayList<>();
        for (int round = 1; round < n; round++) {
            int position = 0;
            for (int i = 0; i < n / 2; i++) {
                Long a = ring.get(i);
                Long b = ring.get(n - 1 - i);
                if (a == null || b == null) continue;
                // Alternate who is listed first so nobody is always "player 1".
                boolean swap = (round + i) % 2 == 0;
                fixtures.add(new Slot(round, position++, swap ? b : a, swap ? a : b, false));
            }
            // Rotate all but the first element clockwise.
            Long last = ring.remove(n - 1);
            ring.add(1, last);
        }
        return fixtures;
    }

    /**
     * Single elimination. {@code seeded} is ordered best first. The bracket is padded with byes to the next
     * power of two and seeds are placed so that seed 1 and 2 can only meet in the final.
     * Returns every fixture of every round; later rounds have null players until winners advance.
     */
    public static List<Slot> knockout(List<Long> seeded) {
        int n = seeded.size();
        if (n < 2) throw new IllegalArgumentException("At least two players are required");
        int size = Integer.highestOneBit(n - 1) << 1;
        int[] order = seedOrder(size);
        List<Slot> fixtures = new ArrayList<>();
        int rounds = Integer.numberOfTrailingZeros(size);
        for (int pos = 0; pos < size / 2; pos++) {
            int seedA = order[pos * 2];
            int seedB = order[pos * 2 + 1];
            Long a = seedA <= n ? seeded.get(seedA - 1) : null;
            Long b = seedB <= n ? seeded.get(seedB - 1) : null;
            fixtures.add(new Slot(1, pos, a, b, a == null || b == null));
        }
        for (int round = 2; round <= rounds; round++) {
            int matches = size >> round;
            for (int pos = 0; pos < matches; pos++) {
                fixtures.add(new Slot(round, pos, null, null, false));
            }
        }
        return fixtures;
    }

    /** Number of knockout rounds for {@code players} entrants. */
    public static int knockoutRounds(int players) {
        int size = Integer.highestOneBit(Math.max(players, 2) - 1) << 1;
        return Integer.numberOfTrailingZeros(size);
    }

    /** Standard bracket order, e.g. size 8 → 1,8,4,5,2,7,3,6. */
    static int[] seedOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int nextSize = order.size() * 2;
            List<Integer> expanded = new ArrayList<>(nextSize);
            for (int seed : order) {
                expanded.add(seed);
                expanded.add(nextSize + 1 - seed);
            }
            order = expanded;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }
}
