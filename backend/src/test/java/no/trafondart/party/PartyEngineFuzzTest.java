package no.trafondart.party;

import no.trafondart.game.CheckoutCalculator;
import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hundreds of random games of every type and size: they always finish, and the bookkeeping always adds up. */
class PartyEngineFuzzTest {

    private static final List<Dart> ALL = new ArrayList<>();

    static {
        ALL.add(Dart.MISS);
        ALL.add(Dart.OUTER_BULL);
        ALL.add(Dart.BULL);
        for (int n = 1; n <= 20; n++) for (int m = 1; m <= 3; m++) ALL.add(new Dart(n, m));
    }

    /** A thrower who aims at something useful about half the time. */
    private static Dart aim(GameType type, PartySettings s, PartyState st, List<Integer> sides, Random r) {
        if (r.nextDouble() > 0.6) return ALL.get(r.nextInt(ALL.size()));
        PartyState.SideState me = st.sides().get(sides.get(st.currentSlot()));
        return switch (type) {
            case X01 -> {
                if (!me.opened()) yield Dart.dbl(20);
                var route = CheckoutCalculator.suggest(me.remaining(), 3 - st.turnDarts().size(), s.outRule());
                yield route.map(List::getFirst).orElse(me.remaining() > 60 ? Dart.treble(20) : Dart.single(1));
            }
            case CRICKET -> {
                for (int n : new int[]{20, 19, 18, 17, 16, 15}) if (me.marks().get(n) < 3) yield Dart.treble(n);
                yield me.marks().get(25) < 3 ? Dart.BULL : Dart.treble(20);
            }
            case KILLER -> {
                int mine = s.killerNumbers().get(st.currentSlot());
                if (!me.killer()) yield Dart.dbl(mine);
                for (int i = 0; i < sides.size(); i++) {
                    if (i != st.currentSlot() && !st.sides().get(sides.get(i)).out()) yield Dart.dbl(s.killerNumbers().get(i));
                }
                yield Dart.MISS;
            }
            case AROUND_THE_CLOCK -> me.target() == 25 ? Dart.BULL : Dart.single(me.target());
        };
    }

    @Test
    void randomGamesAlwaysFinishAndAddUp() {
        Random r = new Random(1404);
        int played = 0;
        for (int game = 0; game < 600; game++) {
            GameType type = GameType.values()[game % GameType.values().length];
            boolean teams = (type == GameType.X01 || type == GameType.CRICKET) && r.nextInt(3) == 0;
            int n = teams ? 4 : switch (type) {
                case X01 -> 3 + r.nextInt(2);
                case CRICKET -> 2 + r.nextInt(3);
                case KILLER -> 2 + r.nextInt(7);
                case AROUND_THE_CLOCK -> 1 + r.nextInt(8);
            };
            List<Integer> sides = new ArrayList<>();
            for (int i = 0; i < n; i++) sides.add(teams ? i % 2 : i);
            List<Integer> numbers = new ArrayList<>();
            if (type == GameType.KILLER) {
                List<Integer> pool = new ArrayList<>();
                for (int i = 1; i <= 20; i++) pool.add(i);
                java.util.Collections.shuffle(pool, r);
                numbers = pool.subList(0, n);
            }
            PartySettings s = PartyRules.normalise(type, teams, n, new PartySettings(
                    List.of(301, 501, 701).get(r.nextInt(3)), InRule.values()[r.nextInt(2)], OutRule.values()[r.nextInt(3)],
                    1 + r.nextInt(2), 1 + r.nextInt(5), r.nextBoolean(), r.nextBoolean() ? 0 : 10 + r.nextInt(21),
                    type == GameType.KILLER ? numbers : null));

            List<Dart> darts = new ArrayList<>();
            PartyState st = PartyEngine.replay(type, s, sides, darts);
            while (!st.finished()) {
                assertThat(darts.size()).as("%s game %d must finish", type, game).isLessThan(5000);
                darts.add(aim(type, s, st, sides, r));
                st = PartyEngine.replay(type, s, sides, darts);
                assertThat(st.dartCount()).isEqualTo(darts.size());
                assertThat(st.turnDarts()).hasSizeLessThanOrEqualTo(3);
            }
            played++;
            check(type, s, sides, st, darts);
            List<Dart> extra = new ArrayList<>(darts);
            extra.add(Dart.MISS);
            PartySettings fs = s;
            assertThatThrownBy(() -> PartyEngine.replay(type, fs, sides, extra)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(played).isEqualTo(600);
    }

    private static void check(GameType type, PartySettings s, List<Integer> sides, PartyState st, List<Dart> darts) {
        int nSides = (int) sides.stream().distinct().count();
        assertThat(st.sides()).hasSize(nSides);
        assertThat(st.winnerSide()).isBetween(0, nSides - 1);
        // every dart was thrown by somebody
        assertThat(st.slots().stream().mapToInt(PartyState.SlotStats::darts).sum()).isEqualTo(darts.size());
        // placements: the winning side shares 1st place, and sides are ranked 1..nSides
        Set<Integer> places = new HashSet<>();
        for (int slot = 0; slot < sides.size(); slot++) {
            PartyState.SlotStats ss = st.slots().get(slot);
            assertThat(ss.slot()).isEqualTo(slot);
            assertThat(ss.placement()).isBetween(1, nSides);
            assertThat(ss.placement() == 1).isEqualTo(sides.get(slot).equals(st.winnerSide()));
            places.add(ss.placement());
            // team mates always share a placement
            for (int other = 0; other < sides.size(); other++) {
                if (sides.get(other).equals(sides.get(slot))) assertThat(st.slots().get(other).placement()).isEqualTo(ss.placement());
            }
        }
        assertThat(places).hasSize(nSides);
        PartyState.SideState winner = st.sides().get(st.winnerSide());
        switch (type) {
            case X01 -> {
                assertThat(winner.legsWon()).isEqualTo(s.legsToWin());
                for (PartyState.SideState side : st.sides()) assertThat(side.remaining()).isBetween(0, s.startScore());
                int points = st.slots().stream().mapToInt(PartyState.SlotStats::score).sum();
                assertThat(points).isGreaterThanOrEqualTo(s.startScore() * s.legsToWin());
            }
            case CRICKET -> {
                assertThat(winner.legsWon()).isEqualTo(s.legsToWin());
                for (PartyState.SideState side : st.sides()) {
                    assertThat(side.marks().keySet()).containsExactlyInAnyOrder(15, 16, 17, 18, 19, 20, 25);
                    assertThat(side.marks().values()).allSatisfy(m -> assertThat(m).isBetween(0, 3));
                    assertThat(side.points()).isNotNegative();
                }
            }
            case KILLER -> {
                assertThat(st.sides().stream().filter(x -> !x.out()).count()).isEqualTo(1);
                assertThat(winner.out()).isFalse();
                int livesTaken = st.slots().stream().mapToInt(PartyState.SlotStats::score).sum();
                int livesLost = st.sides().stream().mapToInt(x -> s.lives() - x.lives()).sum();
                assertThat(livesTaken).isEqualTo(livesLost);
            }
            case AROUND_THE_CLOCK -> {
                assertThat(winner.finished()).isTrue();
                assertThat(st.sides().stream().filter(PartyState.SideState::finished).count()).isEqualTo(1);
                assertThat(st.slots().get(st.winnerSide()).score()).isEqualTo(21);
            }
        }
    }

    @Test
    void settingsAreValidatedPerGame() {
        PartySettings any = new PartySettings(501, InRule.STRAIGHT, OutRule.DOUBLE, 1, 3, false, 20, List.of(1, 2, 3));
        // player counts
        assertThatThrownBy(() -> PartyRules.normalise(GameType.X01, false, 2, any)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.X01, false, 5, any)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.X01, true, 3, any)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.CRICKET, false, 1, any)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.KILLER, false, 9, any)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.KILLER, true, 4, any)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.AROUND_THE_CLOCK, false, 0, any)).isInstanceOf(IllegalArgumentException.class);
        // values
        assertThatThrownBy(() -> PartyRules.normalise(GameType.X01, false, 3,
                new PartySettings(500, InRule.STRAIGHT, OutRule.DOUBLE, 1, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.CRICKET, false, 2,
                new PartySettings(null, null, null, 1, null, null, 5, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.KILLER, false, 3,
                new PartySettings(null, null, null, null, 9, null, null, List.of(1, 2, 3)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.KILLER, false, 3,
                new PartySettings(null, null, null, null, 3, null, null, List.of(1, 1, 3)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartyRules.normalise(GameType.KILLER, false, 3,
                new PartySettings(null, null, null, null, 3, null, null, List.of(1, 2, 21)))).isInstanceOf(IllegalArgumentException.class);
        // defaults, and settings that don't belong to the game are dropped
        PartySettings x01 = PartyRules.normalise(GameType.X01, false, 3, new PartySettings(null, null, null, null, 4, true, 12, List.of(1)));
        assertThat(x01).isEqualTo(new PartySettings(501, InRule.STRAIGHT, OutRule.DOUBLE, 1, null, null, null, null));
        PartySettings cricket = PartyRules.normalise(GameType.CRICKET, true, 4, new PartySettings(null, null, null, null, null, null, null, null));
        assertThat(cricket).isEqualTo(new PartySettings(null, null, null, 1, null, null, 20, null));
        PartySettings atc = PartyRules.normalise(GameType.AROUND_THE_CLOCK, false, 1, new PartySettings(null, null, null, null, null, null, null, null));
        assertThat(atc).isEqualTo(new PartySettings(null, null, null, null, null, false, null, null));
    }

    @Test
    void teamLineupsMustAlternate() {
        assertThatThrownBy(() -> PartyEngine.replay(GameType.X01, new PartySettings(501, InRule.STRAIGHT, OutRule.DOUBLE, 1, null, null, null, null),
                List.of(0, 0, 1, 1), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
