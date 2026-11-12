package no.trafondart.tournament;

import no.trafondart.tournament.BracketGenerator.Slot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every field size from 2 to 64, not just the handful of hand-picked cases. */
class BracketGeneratorPropertiesTest {

    private static List<Long> players(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList(); // id == seed for readability
    }

    @Test
    void knockoutBracketsAreWellFormedForEveryFieldSize() {
        for (int n = 2; n <= 64; n++) {
            List<Slot> slots = BracketGenerator.knockout(players(n));
            int size = Integer.highestOneBit(n - 1) << 1;
            int rounds = BracketGenerator.knockoutRounds(n);
            assertThat(1 << rounds).isEqualTo(size);
            assertThat(slots).as("n=%d: a bracket of %d has %d matches", n, size, size - 1).hasSize(size - 1);

            List<Slot> first = slots.stream().filter(s -> s.round() == 1).toList();
            assertThat(first).hasSize(size / 2);
            Set<Long> seen = new HashSet<>();
            int byes = 0;
            for (Slot s : first) {
                assertThat(s.player1() != null || s.player2() != null).as("n=%d: no empty first-round match", n).isTrue();
                if (s.player1() != null) assertThat(seen.add(s.player1())).isTrue();
                if (s.player2() != null) assertThat(seen.add(s.player2())).isTrue();
                if (s.bye()) {
                    byes++;
                    Long p = s.player1() != null ? s.player1() : s.player2();
                    assertThat(p).as("n=%d: byes go to the top seeds", n).isLessThanOrEqualTo((long) (size - n));
                }
            }
            assertThat(seen).as("n=%d: everyone is in round one exactly once", n).hasSize(n);
            assertThat(byes).isEqualTo(size - n);

            // positions are unique per round and later rounds start empty
            Map<Integer, Set<Integer>> positions = new HashMap<>();
            for (Slot s : slots) {
                assertThat(positions.computeIfAbsent(s.round(), r -> new HashSet<>()).add(s.position())).isTrue();
                if (s.round() > 1) assertThat(s.player1() == null && s.player2() == null && !s.bye()).isTrue();
            }
            // seeds 1 and 2 are in different halves, so they can only meet in the final
            if (n >= 2) {
                int half = size / 4;
                int pos1 = first.stream().filter(s -> Long.valueOf(1).equals(s.player1()) || Long.valueOf(1).equals(s.player2())).findFirst().orElseThrow().position();
                int pos2 = first.stream().filter(s -> Long.valueOf(2).equals(s.player1()) || Long.valueOf(2).equals(s.player2())).findFirst().orElseThrow().position();
                if (size >= 4) assertThat(pos1 < half).isNotEqualTo(pos2 < half);
            }
        }
    }

    @Test
    void roundRobinIsCompleteAndFairForEveryFieldSize() {
        for (int n = 2; n <= 20; n++) {
            List<Slot> fixtures = BracketGenerator.roundRobin(players(n));
            assertThat(fixtures).as("n=%d", n).hasSize(n * (n - 1) / 2);
            Set<String> pairs = new HashSet<>();
            Map<Integer, Set<Long>> byRound = new HashMap<>();
            for (Slot s : fixtures) {
                assertThat(s.player1()).isNotNull().isNotEqualTo(s.player2());
                assertThat(s.player2()).isNotNull();
                long a = Math.min(s.player1(), s.player2()), b = Math.max(s.player1(), s.player2());
                assertThat(pairs.add(a + "-" + b)).as("n=%d: %d-%d only once", n, a, b).isTrue();
                Set<Long> inRound = byRound.computeIfAbsent(s.round(), r -> new HashSet<>());
                assertThat(inRound.add(s.player1()) && inRound.add(s.player2())).as("n=%d: nobody plays twice in round %d", n, s.round()).isTrue();
            }
            int expectedRounds = n % 2 == 0 ? n - 1 : n;
            assertThat(byRound).hasSize(expectedRounds);
        }
    }

    @Test
    void tooFewPlayersIsRejected() {
        assertThatThrownBy(() -> BracketGenerator.knockout(new ArrayList<>(List.of(1L)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BracketGenerator.knockout(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
