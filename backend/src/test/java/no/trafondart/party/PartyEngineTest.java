package no.trafondart.party;

import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;
import no.trafondart.party.PartyState.SideState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static no.trafondart.game.Dart.BULL;
import static no.trafondart.game.Dart.MISS;
import static no.trafondart.game.Dart.OUTER_BULL;
import static no.trafondart.game.Dart.dbl;
import static no.trafondart.game.Dart.single;
import static no.trafondart.game.Dart.treble;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules of the party games, one behaviour per test. */
class PartyEngineTest {

    private static List<Dart> darts(Dart... d) {
        return List.of(d);
    }

    private static List<Dart> repeat(Dart d, int times) {
        return new ArrayList<>(Collections.nCopies(times, d));
    }

    private static List<Dart> concat(List<Dart>... parts) {
        List<Dart> out = new ArrayList<>();
        for (List<Dart> p : parts) out.addAll(p);
        return out;
    }

    // ------------------------------------------------------------------ X01 for three or four, and doubles

    @Nested
    class X01 {
        final PartySettings s301 = new PartySettings(301, InRule.STRAIGHT, OutRule.DOUBLE, 1, null, null, null, null);

        PartyState play(List<Integer> sides, PartySettings s, List<Dart> d) {
            return PartyEngine.replay(GameType.X01, s, sides, d);
        }

        @Test
        void threePlayersThrowInTurnAndKeepTheirOwnScores() {
            PartyState st = play(List.of(0, 1, 2), s301, concat(repeat(treble(20), 3), repeat(single(20), 3), repeat(MISS, 3), darts(single(1))));
            assertThat(st.sides()).extracting(SideState::remaining).containsExactly(120, 241, 301);
            assertThat(st.currentSlot()).isZero();
            assertThat(st.round()).isEqualTo(2);
            assertThat(st.turnDarts()).hasSize(1);
        }

        @Test
        void aBustRestoresTheTurnAndPassesTheBoard() {
            // slot 0 gets to 21 (T20 ×4 + S20 = 260), then busts with T20 next turn
            List<Dart> d = concat(repeat(treble(20), 3), repeat(MISS, 6), darts(treble(20), single(20), MISS), repeat(MISS, 6), darts(treble(20)));
            PartyState st = play(List.of(0, 1, 2), s301, d);
            assertThat(st.sides().getFirst().remaining()).isEqualTo(41);
            assertThat(st.currentSlot()).isEqualTo(1);
            assertThat(st.slots().getFirst().darts()).isEqualTo(7);
        }

        @Test
        void theWinnerOfALegEndsItForEveryoneAndTheNextLegStartsWithTheNextPlayer() {
            PartySettings twoLegs = new PartySettings(301, InRule.STRAIGHT, OutRule.STRAIGHT, 2, null, null, null, null);
            List<Dart> leg1 = concat(repeat(treble(20), 3), repeat(MISS, 6), darts(treble(20), treble(20), single(1)));
            PartyState st = play(List.of(0, 1, 2), twoLegs, leg1);
            assertThat(st.leg()).isEqualTo(2);
            assertThat(st.sides().getFirst().legsWon()).isEqualTo(1);
            assertThat(st.sides()).extracting(SideState::remaining).containsOnly(301);
            assertThat(st.currentSlot()).as("leg 2 starts with the next player").isEqualTo(1);
            assertThat(st.finished()).isFalse();
        }

        @Test
        void doublesShareOneScoreAndTeamMatesAlternate() {
            // slots 0,2 are team A; slots 1,3 are team B
            List<Dart> d = concat(repeat(treble(20), 3), repeat(MISS, 3), repeat(treble(20), 3));
            PartyState st = play(List.of(0, 1, 0, 1), s301, d);
            assertThat(st.sides()).hasSize(2);
            // A1 scores 180 (121 left). A2 hits T20, T20: 1 left is dead with double out, so bust after two darts
            // and back to 121. The ninth dart is B2's.
            assertThat(st.sides().get(0).remaining()).isEqualTo(121);
            assertThat(st.sides().get(1).remaining()).isEqualTo(241);
            assertThat(st.currentSlot()).isEqualTo(3);
            assertThat(st.slots().get(0).darts()).isEqualTo(3);
            assertThat(st.slots().get(2).darts()).isEqualTo(2);
        }

        @Test
        void teamCheckoutWinsForBothPartners() {
            PartySettings s = new PartySettings(301, InRule.STRAIGHT, OutRule.DOUBLE, 1, null, null, null, null);
            // A1 180 (121), B1 misses, A2 T20 S1 D20 (20 left), B2 misses, A1 D10 finishes
            List<Dart> d = concat(repeat(treble(20), 3), repeat(MISS, 3), darts(treble(20), single(1), dbl(20)), repeat(MISS, 3), darts(dbl(10)));
            PartyState st = play(List.of(0, 1, 0, 1), s, d);
            assertThat(st.sides().get(0).remaining()).isEqualTo(0);
            assertThat(st.finished()).isTrue();
            assertThat(st.winnerSide()).isZero();
            assertThat(st.slots()).extracting(PartyState.SlotStats::placement).containsExactly(1, 2, 1, 2);
        }

        @Test
        void doubleInIsPerSide() {
            PartySettings s = new PartySettings(301, InRule.DOUBLE, OutRule.DOUBLE, 1, null, null, null, null);
            PartyState st = play(List.of(0, 1, 2), s, darts(treble(20), dbl(10), treble(20)));
            assertThat(st.sides().getFirst().remaining()).isEqualTo(301 - 20 - 60);
            assertThat(st.sides().getFirst().opened()).isTrue();
            assertThat(st.sides().get(1).opened()).isFalse();
        }

        @Test
        void suggestsACheckoutForTheCurrentSide() {
            PartyState st = play(List.of(0, 1, 2), s301, concat(repeat(treble(20), 3), repeat(MISS, 6), darts(treble(20), single(1))));
            // 301-180-61 = 60 left with one dart: no one-dart finish on 60 with double out
            assertThat(st.checkout()).isEmpty();
            PartyState st2 = play(List.of(0, 1, 2), s301, concat(repeat(treble(20), 3), repeat(MISS, 6)));
            assertThat(st2.checkout()).isNotEmpty();
        }
    }

    // ------------------------------------------------------------------ Cricket

    @Nested
    class Cricket {
        final PartySettings s = new PartySettings(null, null, null, 1, null, null, 20, null);

        PartyState play(List<Integer> sides, List<Dart> d) {
            return PartyEngine.replay(GameType.CRICKET, s, sides, d);
        }

        @Test
        void threeMarksCloseANumberAndExtraMarksScoreWhileSomeoneIsOpen() {
            PartyState st = play(List.of(0, 1), darts(treble(20), treble(20), single(20)));
            SideState a = st.sides().getFirst();
            assertThat(a.marks().get(20)).isEqualTo(3);
            assertThat(a.points()).isEqualTo(80);
            assertThat(st.slots().getFirst().score()).as("marks").isEqualTo(7);
        }

        @Test
        void noPointsOnceEveryoneHasClosedTheNumber() {
            List<Dart> d = concat(darts(treble(20), MISS, MISS), darts(treble(20), single(20), MISS), darts(single(20), MISS, MISS));
            PartyState st = play(List.of(0, 1), d);
            // A closed 20 first, so B's extra single scores nothing; then B has closed it too, so A's scores nothing
            assertThat(st.sides().get(1).points()).isZero();
            assertThat(st.sides().get(0).points()).isZero();
        }

        @Test
        void bullCountsOneOrTwoMarksAndOtherNumbersNothing() {
            PartyState st = play(List.of(0, 1), darts(BULL, OUTER_BULL, treble(14)));
            assertThat(st.sides().getFirst().marks().get(25)).isEqualTo(3);
            assertThat(st.sides().getFirst().marks().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        }

        @Test
        void winningNeedsEverythingClosedAndAtLeastAsManyPoints() {
            List<Dart> closeAll = darts(treble(20), treble(19), treble(18), treble(17), treble(16), treble(15), BULL, OUTER_BULL);
            // A closes everything over three turns with B missing in between; B scored nothing, so A wins on closing
            List<Dart> d = new ArrayList<>();
            for (int i = 0; i < closeAll.size(); i++) {
                d.add(closeAll.get(i));
                if (i % 3 == 2) d.addAll(repeat(MISS, 3));
            }
            PartyState st = play(List.of(0, 1), d);
            assertThat(st.finished()).isTrue();
            assertThat(st.winnerSide()).isZero();
        }

        @Test
        void closingEverythingBehindOnPointsDoesNotWin() {
            List<Dart> d = new ArrayList<>();
            d.addAll(darts(treble(20), treble(20), MISS));     // A: 20 closed + 60
            d.addAll(darts(treble(20), treble(19), treble(18))); // B
            d.addAll(repeat(MISS, 3));
            d.addAll(darts(treble(17), treble(16), treble(15)));
            d.addAll(repeat(MISS, 3));
            d.addAll(darts(BULL, OUTER_BULL, MISS));           // B has closed everything, 0 points < 60
            PartyState st = play(List.of(0, 1), d);
            assertThat(st.finished()).isFalse();
            assertThat(st.sides().get(1).points()).isZero();
        }

        @Test
        void theRoundLimitEndsTheLegOnPoints() {
            PartySettings tenRounds = new PartySettings(null, null, null, 1, null, null, 10, null);
            List<Dart> d = concat(darts(treble(20), treble(20), MISS), repeat(MISS, 3));
            for (int r = 1; r < 10; r++) d.addAll(repeat(MISS, 6));
            PartyState st = PartyEngine.replay(GameType.CRICKET, tenRounds, List.of(0, 1), d);
            assertThat(st.finished()).isTrue();
            assertThat(st.winnerSide()).isZero();
        }

        @Test
        void teamsShareMarks() {
            PartyState st = play(List.of(0, 1, 0, 1), concat(darts(single(20), MISS, MISS), repeat(MISS, 3), darts(dbl(20), single(20), MISS)));
            assertThat(st.sides().get(0).marks().get(20)).isEqualTo(3);
            assertThat(st.sides().get(0).points()).isEqualTo(20);
        }
    }

    // ------------------------------------------------------------------ Killer

    @Nested
    class Killer {
        final PartySettings s = new PartySettings(null, null, null, null, 3, null, null, List.of(5, 10, 15));

        PartyState play(List<Dart> d) {
            return PartyEngine.replay(GameType.KILLER, s, List.of(0, 1, 2), d);
        }

        @Test
        void youNeedYourOwnDoubleBeforeYouCanTakeLives() {
            PartyState st = play(darts(dbl(10), dbl(10), dbl(5)));
            assertThat(st.sides().get(1).lives()).as("not a killer yet").isEqualTo(3);
            assertThat(st.sides().get(0).killer()).isTrue();
        }

        @Test
        void killersTakeLivesWithDoublesOnly() {
            PartyState st = play(darts(dbl(5), dbl(10), treble(10), single(10), MISS, MISS, MISS, MISS, MISS, dbl(10)));
            assertThat(st.sides().get(1).lives()).isEqualTo(1);
            assertThat(st.slots().get(0).score()).as("lives taken").isEqualTo(2);
        }

        @Test
        void eliminatedPlayersAreSkippedAndTheLastOneStandingWins() {
            List<Dart> d = concat(darts(dbl(5), dbl(10), dbl(10)), repeat(MISS, 3), repeat(MISS, 3), darts(dbl(10)));
            PartyState mid = play(d);
            assertThat(mid.sides().get(1).out()).isTrue();
            assertThat(mid.currentSlot()).isZero();
            List<Dart> end = concat(d, darts(dbl(15), dbl(15)), repeat(MISS, 3), darts(dbl(15)));
            PartyState st = play(end);
            assertThat(st.finished()).isTrue();
            assertThat(st.winnerSide()).isZero();
            assertThat(st.slots()).extracting(PartyState.SlotStats::placement).containsExactly(1, 3, 2);
        }

        @Test
        void hittingYourOwnDoubleAsAKillerDoesNoHarm() {
            PartyState st = play(darts(dbl(5), dbl(5), dbl(5)));
            assertThat(st.sides().getFirst().lives()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------ Around the Clock

    @Nested
    class AroundTheClock {
        PartyState play(boolean skips, List<Integer> sides, List<Dart> d) {
            return PartyEngine.replay(GameType.AROUND_THE_CLOCK, new PartySettings(null, null, null, null, null, skips, null, null), sides, d);
        }

        @Test
        void numbersMustBeHitInOrder() {
            PartyState st = play(false, List.of(0, 1), darts(single(2), single(1), treble(2)));
            assertThat(st.sides().getFirst().target()).isEqualTo(3);
        }

        @Test
        void withSkipsDoublesAndTreblesJumpAhead() {
            PartyState st = play(true, List.of(0, 1), darts(treble(1), dbl(4), single(6)));
            assertThat(st.sides().getFirst().target()).isEqualTo(7);
        }

        @Test
        void aBullAfterTwentyFinishesAndEndsTheGame() {
            List<Dart> d = new ArrayList<>();
            for (int n = 1; n <= 20; n++) {
                d.add(single(n));
                if (d.size() % 3 == 0) d.addAll(repeat(MISS, 3));
            }
            d.add(OUTER_BULL);
            PartyState st = play(false, List.of(0, 1), d);
            assertThat(st.finished()).isTrue();
            assertThat(st.winnerSide()).isZero();
            assertThat(st.slots().getFirst().darts()).isEqualTo(21);
        }

        @Test
        void soloPracticeWorks() {
            List<Dart> d = new ArrayList<>();
            for (int n = 1; n <= 20; n++) d.add(single(n));
            d.add(BULL);
            PartyState st = play(false, List.of(0), d);
            assertThat(st.finished()).isTrue();
            assertThat(st.slots().getFirst().darts()).isEqualTo(21);
            assertThat(st.slots().getFirst().score()).isEqualTo(21);
        }
    }

    @Test
    void nothingCanBeThrownAfterTheGameIsOver() {
        List<Dart> d = new ArrayList<>();
        for (int n = 1; n <= 20; n++) d.add(single(n));
        d.add(BULL);
        d.add(MISS);
        assertThatThrownBy(() -> PartyEngine.replay(GameType.AROUND_THE_CLOCK, new PartySettings(null, null, null, null, null, false, null, null), List.of(0), d))
                .isInstanceOf(IllegalStateException.class);
    }
}
