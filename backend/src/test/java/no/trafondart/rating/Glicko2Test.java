package no.trafondart.rating;

import no.trafondart.rating.Glicko2.Rating;
import no.trafondart.rating.Glicko2.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Glicko2Test {

    /** The worked example from Glickman's "Example of the Glicko-2 system". */
    @Test
    void matchesGlickmansWorkedExample() {
        Rating player = new Rating(1500, 200, 0.06);
        Rating updated = Glicko2.update(player, List.of(
                new Result(new Rating(1400, 30, 0.06), 1),
                new Result(new Rating(1550, 100, 0.06), 0),
                new Result(new Rating(1700, 300, 0.06), 0)));

        assertThat(updated.rating()).isCloseTo(1464.06, within(0.01));
        assertThat(updated.rd()).isCloseTo(151.52, within(0.01));
        assertThat(updated.volatility()).isCloseTo(0.05999, within(0.00001));
    }

    @Test
    void winnerGoesUpLoserGoesDownSymmetricallyForEqualPlayers() {
        Rating a = Rating.initial();
        Rating b = Rating.initial();
        Rating a2 = Glicko2.update(a, List.of(new Result(b, 1)));
        Rating b2 = Glicko2.update(b, List.of(new Result(a, 0)));
        assertThat(a2.rating()).isGreaterThan(1500);
        assertThat(b2.rating()).isLessThan(1500);
        assertThat(a2.rating() - 1500).isCloseTo(1500 - b2.rating(), within(0.0001));
        assertThat(a2.rd()).isLessThan(350);
    }

    @Test
    void inactivityGrowsRdButNeverPastTheDefault() {
        Rating settled = new Rating(1600, 60, 0.06);
        Rating afterTenWeeks = Glicko2.inflateForInactivity(settled, 10);
        assertThat(afterTenWeeks.rd()).isGreaterThan(60).isLessThan(350);
        assertThat(Glicko2.inflateForInactivity(settled, 100_000).rd()).isEqualTo(350);
        assertThat(afterTenWeeks.rating()).isEqualTo(1600);
    }

    @Test
    void winProbabilityFavoursHigherRating() {
        double p = Glicko2.winProbability(new Rating(1700, 50, 0.06), new Rating(1500, 50, 0.06));
        assertThat(p).isGreaterThan(0.7).isLessThan(0.8);
    }
}
