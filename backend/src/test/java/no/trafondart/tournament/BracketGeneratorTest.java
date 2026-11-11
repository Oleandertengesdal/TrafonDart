package no.trafondart.tournament;

import no.trafondart.tournament.BracketGenerator.Slot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class BracketGeneratorTest {

    private static List<Long> players(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8})
    void roundRobinPairsEveryoneExactlyOnce(int n) {
        List<Slot> fixtures = BracketGenerator.roundRobin(players(n));
        assertThat(fixtures).hasSize(n * (n - 1) / 2);
        Set<String> pairs = new HashSet<>();
        for (Slot s : fixtures) {
            long a = Math.min(s.player1(), s.player2());
            long b = Math.max(s.player1(), s.player2());
            assertThat(a).isNotEqualTo(b);
            assertThat(pairs.add(a + "-" + b)).as("duplicate pairing %s-%s", a, b).isTrue();
        }
    }

    @Test
    void roundRobinNobodyPlaysTwiceInOneRound() {
        List<Slot> fixtures = BracketGenerator.roundRobin(players(6));
        fixtures.stream().collect(java.util.stream.Collectors.groupingBy(Slot::round)).forEach((round, list) -> {
            Set<Long> seen = new HashSet<>();
            list.forEach(s -> {
                assertThat(seen.add(s.player1())).isTrue();
                assertThat(seen.add(s.player2())).isTrue();
            });
        });
    }

    @Test
    void seedOrderKeepsTopSeedsApart() {
        assertThat(BracketGenerator.seedOrder(8)).containsExactly(1, 8, 4, 5, 2, 7, 3, 6);
        assertThat(BracketGenerator.seedOrder(4)).containsExactly(1, 4, 2, 3);
    }

    @Test
    void knockoutWithFiveGivesTopSeedsByes() {
        List<Slot> fixtures = BracketGenerator.knockout(players(5));
        // bracket of 8: 4 first-round fixtures, 2 semis, 1 final
        assertThat(fixtures).hasSize(7);
        List<Slot> round1 = fixtures.stream().filter(s -> s.round() == 1).toList();
        assertThat(round1.stream().filter(Slot::bye).count()).isEqualTo(3);
        // seed 1 (player 1) has a bye
        Slot first = round1.getFirst();
        assertThat(first.player1()).isEqualTo(1L);
        assertThat(first.player2()).isNull();
        assertThat(BracketGenerator.knockoutRounds(5)).isEqualTo(3);
    }

    @Test
    void knockoutWithTwoIsJustAFinal() {
        List<Slot> fixtures = BracketGenerator.knockout(players(2));
        assertThat(fixtures).hasSize(1);
        assertThat(fixtures.getFirst().bye()).isFalse();
    }
}
