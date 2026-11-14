package no.trafondart.bot;

import no.trafondart.game.Dart;
import no.trafondart.game.GameConfig;
import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;
import no.trafondart.game.X01Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Trafo, the practice bot: where it aims, where its darts land, and how good each level is. */
class BotPlayerTest {

    private static final GameConfig D501 = new GameConfig(501, InRule.STRAIGHT, OutRule.DOUBLE, 1);

    // ---------------------------------------------------------------- the board

    @ParameterizedTest(name = "{0} mm at {1}° -> {2}")
    @CsvSource({
            "0, 90, BULL",
            "6, 0, BULL",
            "10, 45, 25",
            "103, 90, T20",       // straight up through the treble ring: 20 is at the top
            "166, 90, D20",
            "134, 90, S20",
            "60, 90, S20",        // inner single
            "103, 72, T1",        // one segment clockwise from 20
            "103, 108, T5",       // one segment anticlockwise from 20
            "103, 270, T3",       // straight down
            "103, 0, T6",         // to the right
            "103, 180, T11",      // to the left
            "166, 252, D19",
            "175, 90, MISS",      // outside the double ring
    })
    void aPointOnTheBoardScoresWhatARealBoardWould(double radius, double degrees, String label) {
        double a = Math.toRadians(degrees);
        assertThat(Dartboard.hit(radius * Math.cos(a), radius * Math.sin(a)).label()).isEqualTo(label);
    }

    @ParameterizedTest(name = "aiming at {0} with a perfect hand hits it")
    @CsvSource({"T20", "T19", "D16", "D1", "S5", "BULL", "25", "D20", "T17"})
    void theAimPointOfEveryTargetIsInsideIt(String label) {
        Dart target = parse(label);
        double[] p = Dartboard.aimPoint(target);
        assertThat(Dartboard.hit(p[0], p[1])).isEqualTo(target);
    }

    // ---------------------------------------------------------------- where it aims

    @ParameterizedTest(name = "{0} left with {1} darts ({2} out) -> aims at {3}")
    @CsvSource({
            "501, 3, DOUBLE, T20",
            "200, 3, DOUBLE, T20",
            "169, 3, DOUBLE, T20",      // no finish (bogey): score
            "170, 3, DOUBLE, T20",      // T20 T20 BULL
            "100, 3, DOUBLE, T20",      // T20 D20
            "40, 3, DOUBLE, D20",
            "32, 1, DOUBLE, D16",
            "50, 1, DOUBLE, BULL",
            "99, 1, DOUBLE, T19",       // can't finish with one dart: set up the next turn
            "20, 3, STRAIGHT, S20",
            "3, 1, STRAIGHT, S3",
            "60, 1, MASTER, T20",
    })
    void itAimsLikeAPlayer(int remaining, int dartsLeft, OutRule out, String expected) {
        assertThat(BotPlayer.aim(remaining, dartsLeft, true, out).label()).isEqualTo(expected);
    }

    @Test
    void withDoubleInItGoesForADoubleUntilItIsIn() {
        assertThat(BotPlayer.aim(501, 3, false, OutRule.DOUBLE).isDouble()).isTrue();
    }

    // ---------------------------------------------------------------- where its darts land

    @Test
    void aSteadyHandHitsWhatItAimsAt() {
        SplittableRandom rng = new SplittableRandom(1);
        int hits = 0;
        for (int i = 0; i < 1000; i++) if (BotPlayer.throwAt(Dart.treble(20), 0.0, rng).equals(Dart.treble(20))) hits++;
        assertThat(hits).isEqualTo(1000);
    }

    @Test
    void aShakyHandMissesIntoTheNeighbours() {
        SplittableRandom rng = new SplittableRandom(2);
        Map<String, Integer> seen = new java.util.HashMap<>();
        for (int i = 0; i < 3000; i++) seen.merge(BotPlayer.throwAt(Dart.treble(20), BotLevel.CLUB.sigma(), rng).label(), 1, Integer::sum);
        // Misses fall around T20: the single 20 most of all, then the 1 and the 5 on either side.
        assertThat(seen.get("S20")).isGreaterThan(seen.get("T20"));
        assertThat(seen).containsKeys("S1", "S5", "T20");
        assertThat(seen.getOrDefault("S3", 0)).isZero(); // the 3 is at the bottom of the board
    }

    @Test
    void theSameMatchGivesTheSameDarts() {
        X01Engine.MatchState s = X01Engine.replay(D501, 1, List.of());
        Dart a = BotPlayer.nextDart(s, 1, BotLevel.LEAGUE, 42L);
        Dart b = BotPlayer.nextDart(s, 1, BotLevel.LEAGUE, 42L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentDartsInAMatchAreNotTheSame() {
        List<Dart> darts = new ArrayList<>();
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (int i = 0; i < 30; i++) {
            X01Engine.MatchState s = X01Engine.replay(new GameConfig(501, InRule.STRAIGHT, OutRule.DOUBLE, 3), 0, darts);
            Dart d = BotPlayer.nextDart(s, s.currentPlayerIndex(), BotLevel.CASUAL, 7L);
            labels.add(d.label());
            darts.add(d);
            if (s.finished()) break;
        }
        assertThat(labels.size()).isGreaterThan(3);
    }

    // ---------------------------------------------------------------- how good each level is

    /** Two bots of the same level play many legs of 501, double out; returns {3-dart average, checkout %}. */
    private static double[] simulate(BotLevel level, int legs) {
        long points = 0, darts = 0, attempts = 0, hits = 0;
        for (int leg = 0; leg < legs; leg++) {
            List<Dart> thrown = new ArrayList<>();
            X01Engine.MatchState s = X01Engine.replay(D501, leg % 2, thrown);
            while (!s.finished()) {
                thrown.add(BotPlayer.nextDart(s, s.currentPlayerIndex(), level, 1000L + leg));
                s = X01Engine.replay(D501, leg % 2, thrown);
                assertThat(thrown.size()).as("a leg ends").isLessThan(600);
            }
            for (X01Engine.PlayerStats p : s.stats()) {
                points += p.pointsScored();
                darts += p.dartsThrown();
                attempts += p.checkoutAttempts();
                hits += p.checkoutsHit();
            }
        }
        return new double[]{points * 3.0 / darts, hits * 100.0 / attempts};
    }

    @ParameterizedTest(name = "{0} averages about what its name says")
    @EnumSource(BotLevel.class)
    void eachLevelAveragesAboutWhatItPromises(BotLevel level) {
        double average = simulate(level, 120)[0];
        assertThat(average).isBetween(level.typicalAverage() - 6.0, level.typicalAverage() + 6.0);
    }

    @Test
    void everyLevelIsBetterThanTheOneBelowIt() {
        Map<BotLevel, double[]> results = new EnumMap<>(BotLevel.class);
        for (BotLevel l : BotLevel.values()) results.put(l, simulate(l, 80));
        BotLevel[] levels = BotLevel.values();
        for (int i = 1; i < levels.length; i++) {
            assertThat(results.get(levels[i])[0]).as("average of " + levels[i]).isGreaterThan(results.get(levels[i - 1])[0]);
            assertThat(results.get(levels[i])[1]).as("checkout %% of " + levels[i]).isGreaterThan(results.get(levels[i - 1])[1]);
        }
    }

    private static Dart parse(String label) {
        if (label.equals("BULL")) return Dart.BULL;
        if (label.equals("25")) return Dart.OUTER_BULL;
        int n = Integer.parseInt(label.substring(1));
        return switch (label.charAt(0)) {
            case 'T' -> Dart.treble(n);
            case 'D' -> Dart.dbl(n);
            default -> Dart.single(n);
        };
    }
}
