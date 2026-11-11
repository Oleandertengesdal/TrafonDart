package no.trafondart.analysis;

import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig;
import no.trafondart.game.X01Engine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static no.trafondart.game.Dart.dbl;
import static no.trafondart.game.Dart.single;
import static no.trafondart.game.Dart.treble;
import static org.assertj.core.api.Assertions.assertThat;

class DartAnalyticsTest {

    /** Two legs of 301 straight-in double-out, first to 2. Player 0 wins both: one hold, one break. */
    private static X01Engine.MatchState twoLegs() {
        List<Dart> d = new ArrayList<>();
        // leg 1, p0 starts: 180, p1 3 misses, p0 T20 S20 S1 (81) -> 40, p1 misses, p0 D20
        d.addAll(List.of(treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), single(20), single(1), Dart.MISS, Dart.MISS, Dart.MISS, dbl(20)));
        // leg 2, p1 starts: p1 S1 S1 S1, p0 180, p1 bust-free misses, p0 T20 T19 (117) -> 4, p1 misses, p0 D2
        d.addAll(List.of(single(1), single(1), single(1), treble(20), treble(20), treble(20),
                Dart.MISS, Dart.MISS, Dart.MISS, treble(20), treble(19), Dart.MISS,
                Dart.MISS, Dart.MISS, Dart.MISS, dbl(2)));
        return X01Engine.replay(new GameConfig(301, GameConfig.InRule.STRAIGHT, GameConfig.OutRule.DOUBLE, 2), 0, d);
    }

    @Test
    void aggregatesHoldBreakBestLegAndFinishes() {
        X01Engine.MatchState s = twoLegs();
        assertThat(s.finished()).isTrue();
        DartAnalytics.Accumulator a = new DartAnalytics.Accumulator();
        a.addMatch(s, 0);

        assertThat(a.legsWon()).isEqualTo(2);
        assertThat(a.hold()).isEqualTo(new DartAnalytics.Rate(1, 1));
        assertThat(a.breaks()).isEqualTo(new DartAnalytics.Rate(1, 1));
        assertThat(a.bestLeg()).isEqualTo(7);
        assertThat(a.finishes(5)).extracting(DartAnalytics.LabelCount::label).containsExactlyInAnyOrder("D20", "D2");
        assertThat(a.favouriteSegment()).isEqualTo(20);
        // bands: two 180s, one 81, one 117, one 40, one 4 (the checkout turns count too)
        assertThat(a.bands().get(7).count()).isEqualTo(2);
        assertThat(a.bands().get(5).count()).isEqualTo(1);
        assertThat(a.trebleRate()).isGreaterThan(40);
    }

    @Test
    void loserSideCountsMissesAndBreaks() {
        DartAnalytics.Accumulator b = new DartAnalytics.Accumulator();
        b.addMatch(twoLegs(), 1);
        assertThat(b.legsWon()).isZero();
        assertThat(b.hold()).isEqualTo(new DartAnalytics.Rate(0, 1));
        assertThat(b.missRate()).isGreaterThan(50);
        assertThat(b.bestLeg()).isNull();
    }
}
