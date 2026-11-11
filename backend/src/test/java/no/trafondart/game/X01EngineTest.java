package no.trafondart.game;

import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;
import no.trafondart.game.X01Engine.EventType;
import no.trafondart.game.X01Engine.MatchState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static no.trafondart.game.Dart.dbl;
import static no.trafondart.game.Dart.single;
import static no.trafondart.game.Dart.treble;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class X01EngineTest {

    private static final GameConfig D501 = new GameConfig(501, InRule.STRAIGHT, OutRule.DOUBLE, 1);

    private static List<Dart> darts(Dart... d) {
        return new ArrayList<>(Arrays.asList(d));
    }

    @Test
    void turnsAlternateAfterThreeDarts() {
        MatchState s = X01Engine.replay(D501, 0, darts(treble(20), treble(20), treble(20)));
        assertThat(s.remaining()).containsExactly(321, 501);
        assertThat(s.currentPlayerIndex()).isEqualTo(1);
        assertThat(s.stats()[0].count180()).isEqualTo(1);
        assertThat(s.stats()[0].threeDartAverage()).isEqualTo(180.0);
    }

    @Test
    void goingBelowZeroIsABustAndResetsTheTurn() {
        // Player 0 gets to 40 then throws 20, 20+... let's build: 501 -> 41 after 8 turns
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.DOUBLE, 1);
        List<Dart> d = darts(
                treble(20), treble(20), treble(20), // p0: 121
                single(1), single(1), single(1),     // p1: 298
                treble(20), single(1), Dart.MISS,    // p0: 60
                single(1), single(1), single(1),     // p1: 295
                single(20), treble(20)               // p0: 40 then 60 -> bust
        );
        MatchState s = X01Engine.replay(cfg, 0, d);
        assertThat(s.lastEvent()).isEqualTo(EventType.BUST);
        assertThat(s.remaining()[0]).isEqualTo(60);
        assertThat(s.currentPlayerIndex()).isEqualTo(1);
        assertThat(s.lastCompletedTurn().bust()).isTrue();
        assertThat(s.lastCompletedTurn().scored()).isZero();
        // bust darts still count as thrown, scoring zero
        assertThat(s.stats()[0].dartsThrown()).isEqualTo(8);
        assertThat(s.stats()[0].pointsScored()).isEqualTo(241);
    }

    @Test
    void leavingOneUnderDoubleOutIsABust() {
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.DOUBLE, 1);
        List<Dart> d = darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS, // p0 121
                treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS, Dart.MISS,  // p0 1
                single(1));
        // p0 at 121 -> 1 after T20 T20 is dead remainder? 121-60 = 61, 61-60=1 -> bust
        MatchState s = X01Engine.replay(cfg, 0, d.subList(0, 8));
        assertThat(s.lastEvent()).isEqualTo(EventType.BUST);
        assertThat(s.remaining()[0]).isEqualTo(121);
    }

    @Test
    void finishingOnASingleUnderDoubleOutIsABust() {
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.DOUBLE, 1);
        // 301 - 180 - 81 = 40 then single 20 + single 20 = 0 on a single -> bust
        List<Dart> d = darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), single(20), single(1), Dart.MISS, Dart.MISS, Dart.MISS,
                single(20), single(20));
        MatchState s = X01Engine.replay(cfg, 0, d);
        assertThat(s.lastEvent()).isEqualTo(EventType.BUST);
        assertThat(s.remaining()[0]).isEqualTo(40);
    }

    @Test
    void checkoutOnDoubleWinsLegAndMatch() {
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.DOUBLE, 1);
        List<Dart> d = darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), single(20), single(1), Dart.MISS, Dart.MISS, Dart.MISS,
                dbl(20));
        MatchState s = X01Engine.replay(cfg, 0, d);
        assertThat(s.finished()).isTrue();
        assertThat(s.winnerIndex()).isZero();
        assertThat(s.lastEvent()).isEqualTo(EventType.MATCH_WON);
        assertThat(s.stats()[0].highestCheckout()).isEqualTo(40);
        assertThat(s.stats()[0].checkoutsHit()).isEqualTo(1);
        assertThat(s.stats()[0].checkoutAttempts()).isEqualTo(1);
        assertThat(s.legs().getFirst().checkoutScore()).isEqualTo(40);
        assertThatThrownBy(() -> X01Engine.replay(cfg, 0, append(d, Dart.MISS)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legsAlternateStarterAndMatchNeedsLegsToWin() {
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.STRAIGHT, 2);
        List<Dart> leg1 = darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), treble(20), single(1)); // p0 wins leg 1 (121 -> 0 with straight out)
        MatchState s = X01Engine.replay(cfg, 0, leg1);
        assertThat(s.lastEvent()).isEqualTo(EventType.LEG_WON);
        assertThat(s.legsWon()).containsExactly(1, 0);
        assertThat(s.currentLeg().legNo()).isEqualTo(2);
        assertThat(s.currentLeg().starterIndex()).isEqualTo(1);
        assertThat(s.currentPlayerIndex()).isEqualTo(1);
        assertThat(s.remaining()).containsExactly(301, 301);
        assertThat(s.finished()).isFalse();
    }

    @Test
    void doubleInIgnoresDartsUntilADoubleLands() {
        GameConfig cfg = new GameConfig(301, InRule.DOUBLE, OutRule.DOUBLE, 1);
        MatchState s = X01Engine.replay(cfg, 0, darts(treble(20), dbl(10), treble(20)));
        assertThat(s.remaining()[0]).isEqualTo(301 - 20 - 60);
        assertThat(s.opened()[0]).isTrue();
        assertThat(s.opened()[1]).isFalse();
        assertThat(s.stats()[0].pointsScored()).isEqualTo(80);
        assertThat(s.stats()[0].dartsThrown()).isEqualTo(3);
    }

    @Test
    void bullseyeOpensDoubleInAndFinishesDoubleOut() {
        GameConfig cfg = new GameConfig(301, InRule.DOUBLE, OutRule.DOUBLE, 1);
        MatchState s = X01Engine.replay(cfg, 0, darts(Dart.BULL));
        assertThat(s.remaining()[0]).isEqualTo(251);
    }

    @Test
    void masterOutAllowsTrebleFinishAndBustsOnOne() {
        GameConfig cfg = new GameConfig(301, InRule.STRAIGHT, OutRule.MASTER, 1);
        List<Dart> base = darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS, Dart.MISS); // p0: 1? no: 121-120 = 1 -> bust
        MatchState s = X01Engine.replay(cfg, 0, base);
        assertThat(s.remaining()[0]).isEqualTo(121);
        MatchState win = X01Engine.replay(cfg, 0, darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), treble(19), single(4)));
        // 121 - 60 - 57 = 4 then S4 under master out -> bust
        assertThat(win.lastEvent()).isEqualTo(EventType.BUST);
        MatchState win2 = X01Engine.replay(cfg, 0, darts(
                treble(20), treble(20), treble(20), Dart.MISS, Dart.MISS, Dart.MISS,
                treble(20), treble(20),                 // p0 busts on 1
                Dart.MISS, Dart.MISS, Dart.MISS,        // p1
                single(1), treble(20), treble(20)));    // p0: 121 -> 120 -> 60 -> 0 on a treble
        assertThat(win2.lastEvent()).isEqualTo(EventType.MATCH_WON);
        assertThat(win2.finished()).isTrue();
    }

    @Test
    void firstNineAverageOnlyUsesFirstThreeTurnsOfEachLeg() {
        List<Dart> d = new ArrayList<>();
        for (int turn = 0; turn < 4; turn++) {
            d.addAll(List.of(treble(20), treble(20), treble(20))); // p0 scores 180 per turn... until it can't
            d.addAll(List.of(single(1), single(1), single(1)));
        }
        // p0: 501 -> 321 -> 141 -> bust? 141 - 180 < 0 -> bust on 3rd dart (after T20 T20 = 21, T20 -> -39)
        MatchState s = X01Engine.replay(D501, 0, d);
        assertThat(s.stats()[0].first9Darts()).isEqualTo(9);
        assertThat(s.stats()[0].first9Points()).isEqualTo(360);
        assertThat(s.stats()[0].count180()).isEqualTo(2);
    }

    @Test
    void undoIsJustReplayingOneDartFewer() {
        List<Dart> d = darts(treble(20), treble(20), treble(20));
        MatchState before = X01Engine.replay(D501, 0, d.subList(0, 2));
        assertThat(before.currentPlayerIndex()).isZero();
        assertThat(before.currentTurn().runningTotal()).isEqualTo(120);
        assertThat(before.dartsLeftInTurn()).isEqualTo(1);
    }

    private static List<Dart> append(List<Dart> list, Dart d) {
        List<Dart> copy = new ArrayList<>(list);
        copy.add(d);
        return copy;
    }
}
