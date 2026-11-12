package no.trafondart.rating;

import no.trafondart.rating.Glicko2.Rating;
import no.trafondart.rating.Glicko2.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Properties that must hold for any pair of players, including absurd ones, so a rating can never become NaN. */
class Glicko2PropertiesTest {

    private static Rating random(Random r) {
        return new Rating(200 + r.nextDouble() * 2800, 30 + r.nextDouble() * 320, 0.01 + r.nextDouble() * 0.2);
    }

    private static void assertSane(Rating r) {
        assertThat(Double.isFinite(r.rating())).isTrue();
        assertThat(r.rd()).isGreaterThan(0).isLessThanOrEqualTo(Glicko2.DEFAULT_RD);
        assertThat(r.volatility()).isGreaterThan(0).isLessThan(1);
    }

    @Test
    void winnerNeverLosesRatingAndLoserNeverGainsForRandomPairs() {
        Random rnd = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            Rating a = random(rnd), b = random(rnd);
            Rating aWins = Glicko2.update(a, List.of(new Result(b, 1)));
            Rating bLoses = Glicko2.update(b, List.of(new Result(a, 0)));
            assertSane(aWins);
            assertSane(bLoses);
            assertThat(aWins.rating()).isGreaterThanOrEqualTo(a.rating());
            assertThat(bLoses.rating()).isLessThanOrEqualTo(b.rating());
            assertThat(aWins.rd()).as("playing makes us more certain").isLessThanOrEqualTo(Math.sqrt(a.rd() * a.rd() + Math.pow(aWins.volatility() * 173.7178, 2)) + 1e-6);
        }
    }

    @Test
    void anUpsetMovesRatingsMoreThanAnExpectedResult() {
        Rating strong = new Rating(1900, 60, 0.06);
        Rating weak = new Rating(1300, 60, 0.06);
        double upsetGain = Glicko2.update(weak, List.of(new Result(strong, 1))).rating() - weak.rating();
        double expectedGain = Glicko2.update(strong, List.of(new Result(weak, 1))).rating() - strong.rating();
        assertThat(upsetGain).isGreaterThan(expectedGain * 5);
    }

    @Test
    void uncertainPlayersMoveFasterThanEstablishedOnes() {
        Rating opponent = new Rating(1500, 80, 0.06);
        double newbie = Glicko2.update(new Rating(1500, 350, 0.06), List.of(new Result(opponent, 1))).rating() - 1500;
        double veteran = Glicko2.update(new Rating(1500, 50, 0.06), List.of(new Result(opponent, 1))).rating() - 1500;
        assertThat(newbie).isGreaterThan(veteran * 3);
    }

    @Test
    void winProbabilitiesAreComplementaryAndBounded() {
        Random rnd = new Random(7);
        for (int i = 0; i < 5000; i++) {
            Rating a = random(rnd), b = random(rnd);
            double pa = Glicko2.winProbability(a, b);
            assertThat(pa).isBetween(0.0, 1.0);
            assertThat(pa + Glicko2.winProbability(b, a)).isCloseTo(1.0, within(1e-9));
        }
        Rating x = new Rating(1500, 100, 0.06);
        assertThat(Glicko2.winProbability(x, x)).isCloseTo(0.5, within(1e-12));
    }

    @Test
    void extremeGapsStayFinite() {
        Rating godlike = new Rating(3500, 30, 0.06);
        Rating beginner = new Rating(100, 30, 0.06);
        assertSane(Glicko2.update(godlike, List.of(new Result(beginner, 0))));
        assertSane(Glicko2.update(beginner, List.of(new Result(godlike, 1))));
        assertSane(Glicko2.update(godlike, List.of(new Result(beginner, 1))));
    }

    @Test
    void noGamesOnlyGrowsUncertainty() {
        Rating r = new Rating(1600, 80, 0.06);
        Rating idle = Glicko2.update(r, List.of());
        assertThat(idle.rating()).isEqualTo(r.rating());
        assertThat(idle.rd()).isGreaterThan(r.rd());
        assertThat(Glicko2.inflateForInactivity(r, 0)).isEqualTo(r);
        assertThat(Glicko2.inflateForInactivity(r, -3)).isEqualTo(r);
        assertThat(Glicko2.inflateForInactivity(r, 1_000_000).rd()).isEqualTo(Glicko2.DEFAULT_RD);
    }
}
