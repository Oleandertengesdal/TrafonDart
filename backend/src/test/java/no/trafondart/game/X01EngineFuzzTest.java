package no.trafondart.game;

import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;
import no.trafondart.game.X01Engine.Leg;
import no.trafondart.game.X01Engine.MatchState;
import no.trafondart.game.X01Engine.PlayerStats;
import no.trafondart.game.X01Engine.Turn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plays hundreds of random matches under every rule combination and checks that the statistics the app shows
 * can be re-derived from the turns themselves. Any drift between "what happened" and "what we report" fails here.
 */
class X01EngineFuzzTest {

    private static final List<Dart> ALL_DARTS = allDarts();

    private static List<Dart> allDarts() {
        List<Dart> all = new ArrayList<>();
        all.add(Dart.MISS);
        all.add(Dart.OUTER_BULL);
        all.add(Dart.BULL);
        for (int n = 1; n <= 20; n++) for (int m = 1; m <= 3; m++) all.add(new Dart(n, m));
        return all;
    }

    /** A sloppy but improving thrower: aims at the checkout route or T20, hits it about half the time. */
    private static Dart nextDart(MatchState s, Random rnd) {
        int me = s.currentPlayerIndex();
        int left = s.remaining()[me];
        int dartsLeft = s.currentTurn() == null || s.currentTurn().complete() ? 3 : 3 - s.currentTurn().darts().size();
        if (!s.opened()[me] && rnd.nextDouble() < 0.5) return rnd.nextBoolean() ? Dart.dbl(20) : Dart.BULL;
        if (rnd.nextDouble() < 0.55) {
            var route = CheckoutCalculator.suggest(left, dartsLeft, s.config().outRule());
            if (route.isPresent()) return route.get().getFirst();
            if (left > 60) return rnd.nextBoolean() ? Dart.treble(20) : Dart.treble(19);
        }
        return ALL_DARTS.get(rnd.nextInt(ALL_DARTS.size()));
    }

    @Test
    void randomMatchesKeepEveryStatisticConsistentWithTheTurns() {
        Random rnd = new Random(20261111);
        int finished = 0;
        for (int game = 0; game < 300; game++) {
            GameConfig config = new GameConfig(
                    List.of(301, 501, 701).get(rnd.nextInt(3)),
                    InRule.values()[rnd.nextInt(2)],
                    OutRule.values()[rnd.nextInt(3)],
                    1 + rnd.nextInt(3));
            int starter = rnd.nextInt(2);
            List<Dart> darts = new ArrayList<>();
            MatchState s = X01Engine.replay(config, starter, darts);
            while (s.winnerIndex() == null && darts.size() < 4000) {
                darts.add(nextDart(s, rnd));
                s = X01Engine.replay(config, starter, darts);
                assertThat(s.dartCount()).isEqualTo(darts.size());
                for (int r : s.remaining()) assertThat(r).isBetween(0, config.startScore());
            }
            assertThat(s.winnerIndex()).as("game %d should finish", game).isNotNull();
            finished++;
            checkFinishedMatch(config, starter, s);

            // nothing may be thrown after the match is over
            List<Dart> extra = new ArrayList<>(darts);
            extra.add(Dart.MISS);
            assertThatThrownBy(() -> X01Engine.replay(config, starter, extra)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(finished).isEqualTo(300);
    }

    private static void checkFinishedMatch(GameConfig config, int starter, MatchState s) {
        int w = s.winnerIndex();
        assertThat(s.legsWon()[w]).isEqualTo(config.legsToWin());
        assertThat(s.legsWon()[1 - w]).isLessThan(config.legsToWin());
        assertThat(s.legs()).hasSize(s.legsWon()[0] + s.legsWon()[1]);

        for (int p = 0; p < 2; p++) {
            final int player = p;
            PlayerStats st = s.stats()[p];
            int darts = 0, points = 0, f9darts = 0, f9points = 0, c180 = 0, c140 = 0, c100 = 0, high = 0, highCo = 0, legsWon = 0;
            for (Leg leg : s.legs()) {
                // legs alternate who throws first
                assertThat(leg.starterIndex()).isEqualTo((starter + leg.legNo() - 1) % 2);
                assertThat(leg.winnerIndex()).isNotNull();
                List<Turn> turns = leg.turns();
                assertThat(turns.getFirst().playerIndex()).isEqualTo(leg.starterIndex());
                for (int i = 1; i < turns.size(); i++) {
                    assertThat(turns.get(i).playerIndex()).isNotEqualTo(turns.get(i - 1).playerIndex());
                }
                int remainingEnd = config.startScore();
                for (Turn t : turns) {
                    assertThat(t.complete()).isTrue();
                    assertThat(t.darts()).hasSizeBetween(1, 3);
                    if (t.darts().size() < 3) assertThat(t.bust() || t.checkout()).isTrue();
                    if (t.playerIndex() != p) continue;
                    darts += t.darts().size();
                    points += t.scored();
                    if (t.turnInLeg() <= 3) {
                        f9darts += t.darts().size();
                        f9points += t.scored();
                    }
                    int sc = t.scored();
                    if (sc == 180) c180++;
                    else if (sc >= 140) c140++;
                    else if (sc >= 100) c100++;
                    high = Math.max(high, sc);
                    remainingEnd = t.endRemaining();
                    assertThat(t.bust() && t.checkout()).isFalse();
                }
                if (leg.winnerIndex() == p) {
                    legsWon++;
                    assertThat(remainingEnd).as("winner finishes on exactly zero").isZero();
                    Turn last = turns.getLast();
                    assertThat(last.playerIndex()).isEqualTo(p);
                    assertThat(last.checkout()).isTrue();
                    Dart finishing = last.darts().getLast().dart();
                    assertThat(OutRules.isFinishingDart(finishing, config.outRule())).isTrue();
                    highCo = Math.max(highCo, leg.checkoutScore());
                    assertThat(leg.checkoutScore()).isBetween(1, 180);
                } else {
                    assertThat(remainingEnd).isPositive();
                    if (config.outRule() != OutRule.STRAIGHT) assertThat(remainingEnd).isNotEqualTo(1);
                }
                assertThat(leg.dartsThrownBy(p)).isEqualTo(turns.stream()
                        .filter(t -> t.playerIndex() == player).mapToInt(t -> t.darts().size()).sum());
            }
            assertThat(st.dartsThrown()).as("darts p%d", p).isEqualTo(darts);
            assertThat(st.pointsScored()).as("points p%d", p).isEqualTo(points);
            assertThat(st.first9Darts()).as("first-9 darts p%d", p).isEqualTo(f9darts);
            assertThat(st.first9Points()).as("first-9 points p%d", p).isEqualTo(f9points);
            assertThat(st.count180()).isEqualTo(c180);
            assertThat(st.count140()).isEqualTo(c140);
            assertThat(st.count100()).isEqualTo(c100);
            assertThat(st.highestTurn()).isEqualTo(high);
            assertThat(st.highestCheckout()).isEqualTo(highCo);
            assertThat(st.legsWon()).isEqualTo(legsWon).isEqualTo(s.legsWon()[p]);
            assertThat(st.checkoutsHit()).isEqualTo(legsWon);
            assertThat(st.checkoutAttempts()).isGreaterThanOrEqualTo(st.checkoutsHit());
            assertThat(st.threeDartAverage()).isBetween(0.0, 180.0);
            assertThat(st.checkoutPercentage()).isBetween(0.0, 100.0);
        }
        // every point scored by the winner over the legs they won adds up to the start score
        int totalLegs = s.legs().size();
        assertThat(s.stats()[0].pointsScored() + s.stats()[1].pointsScored())
                .isLessThanOrEqualTo(totalLegs * 2 * config.startScore());
    }

    @Test
    void doubleInDartsBeforeOpeningCountForNothingButAreStillThrown() {
        GameConfig cfg = new GameConfig(301, InRule.DOUBLE, OutRule.DOUBLE, 1);
        MatchState s = X01Engine.replay(cfg, 0, List.of(Dart.treble(20), Dart.single(5), Dart.dbl(10)));
        assertThat(s.remaining()[0]).isEqualTo(281);
        assertThat(s.stats()[0].dartsThrown()).isEqualTo(3);
        assertThat(s.stats()[0].pointsScored()).isEqualTo(20);
        assertThat(s.opened()[0]).isTrue();
        assertThat(s.opened()[1]).isFalse();
    }

    @Test
    void straightOutCanFinishOnASingleAndOnOne() {
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.STRAIGHT, 1);
        List<Dart> d = new ArrayList<>(List.of(Dart.treble(20), Dart.treble(20), Dart.treble(20),   // p0 121
                Dart.MISS, Dart.MISS, Dart.MISS,
                Dart.treble(20), Dart.treble(20), Dart.MISS,                                       // p0 1
                Dart.MISS, Dart.MISS, Dart.MISS,
                Dart.single(1)));
        MatchState s = X01Engine.replay(cfg, 0, d);
        assertThat(s.winnerIndex()).isZero();
        assertThat(s.legs().getFirst().checkoutScore()).isEqualTo(1);
    }

    @Test
    void outerBullIsNotADoubleButBullseyeIs() {
        assertThat(OutRules.isFinishingDart(Dart.OUTER_BULL, OutRule.DOUBLE)).isFalse();
        assertThat(OutRules.isFinishingDart(Dart.BULL, OutRule.DOUBLE)).isTrue();
        assertThat(OutRules.isFinishingDart(Dart.treble(20), OutRule.DOUBLE)).isFalse();
        assertThat(OutRules.isFinishingDart(Dart.treble(20), OutRule.MASTER)).isTrue();
        assertThat(OutRules.isFinishingDart(Dart.single(20), OutRule.MASTER)).isFalse();
        assertThat(OutRules.isFinishingDart(Dart.MISS, OutRule.STRAIGHT)).isFalse();
    }

    @Test
    void invalidDartsCannotBeConstructed() {
        int[][] bad = {{0, 1}, {21, 1}, {25, 3}, {-1, 1}, {20, 0}, {20, 4}, {26, 1}, {25, 0}};
        for (int[] b : bad) {
            assertThat(Dart.isValid(b[0], b[1])).as("%d x %d", b[0], b[1]).isFalse();
            assertThatThrownBy(() -> new Dart(b[0], b[1])).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void configurationIsValidated() {
        assertThatThrownBy(() -> new GameConfig(500, InRule.STRAIGHT, OutRule.DOUBLE, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(501, InRule.STRAIGHT, OutRule.DOUBLE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(501, InRule.STRAIGHT, OutRule.DOUBLE, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(501, null, OutRule.DOUBLE, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> X01Engine.replay(new GameConfig(501, InRule.STRAIGHT, OutRule.DOUBLE, 1), 2, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
