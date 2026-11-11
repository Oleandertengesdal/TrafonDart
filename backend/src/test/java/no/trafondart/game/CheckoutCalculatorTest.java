package no.trafondart.game;

import no.trafondart.game.GameConfig.OutRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutCalculatorTest {

    private static String route(int remaining, int darts, OutRule rule) {
        return CheckoutCalculator.suggest(remaining, darts, rule)
                .map(r -> r.stream().map(Dart::label).collect(Collectors.joining(" ")))
                .orElse("none");
    }

    @ParameterizedTest(name = "{0} with {1} darts -> {2}")
    @CsvSource({
            "170, 3, T20 T20 BULL",
            "167, 3, T20 T19 BULL",
            "160, 3, T20 T20 D20",
            "120, 3, T20 S20 D20",
            "100, 3, T20 D20",
            "100, 2, T20 D20",
            "61, 2, T15 D8",
            "50, 1, BULL",
            "40, 1, D20",
            "32, 1, D16",
            "3, 2, S1 D1",
            "2, 1, D1"
    })
    void classicDoubleOutRoutes(int remaining, int darts, String expected) {
        assertThat(route(remaining, darts, OutRule.DOUBLE)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {169, 168, 166, 165, 163, 162, 159})
    void bogeyNumbersHaveNoCheckout(int remaining) {
        assertThat(CheckoutCalculator.isBogey(remaining, OutRule.DOUBLE)).isTrue();
    }

    @Test
    void notEnoughDartsLeftMeansNoRoute() {
        assertThat(route(100, 1, OutRule.DOUBLE)).isEqualTo("none");
        assertThat(route(171, 3, OutRule.DOUBLE)).isEqualTo("none");
        assertThat(route(1, 3, OutRule.DOUBLE)).isEqualTo("none");
    }

    @Test
    void masterOutAllowsTrebleFinish() {
        assertThat(route(60, 1, OutRule.MASTER)).isEqualTo("T20");
        assertThat(route(57, 1, OutRule.MASTER)).isEqualTo("T19");
        assertThat(route(180, 3, OutRule.MASTER)).isEqualTo("T20 T20 T20");
    }

    @Test
    void straightOutFinishesOnAnything() {
        assertThat(route(1, 1, OutRule.STRAIGHT)).isEqualTo("S1");
        assertThat(route(180, 3, OutRule.STRAIGHT)).isEqualTo("T20 T20 T20");
    }

    @Test
    void everyRouteActuallyAddsUpAndEndsLegally() {
        for (OutRule rule : OutRule.values()) {
            for (int darts = 1; darts <= 3; darts++) {
                for (int score = 1; score <= 180; score++) {
                    var maybe = CheckoutCalculator.suggest(score, darts, rule);
                    if (maybe.isEmpty()) continue;
                    List<Dart> r = maybe.get();
                    assertThat(r.size()).isLessThanOrEqualTo(darts);
                    assertThat(r.stream().mapToInt(Dart::points).sum()).isEqualTo(score);
                    assertThat(OutRules.isFinishingDart(r.getLast(), rule)).isTrue();
                }
            }
        }
    }
}
