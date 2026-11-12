package no.trafondart.awards;

import no.trafondart.awards.AwardEntities.Award;
import no.trafondart.awards.MonthlyScore.Input;
import no.trafondart.user.UserDtos.PlayerSummary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** "Doing more of a good thing never costs you points" – checked for thousands of random months. */
class MonthlyScorePropertiesTest {

    private static Input random(Random r) {
        int matches = r.nextInt(30);
        return new Input(matches, matches == 0 ? 0 : r.nextInt(matches + 1),
                r.nextBoolean() ? null : r.nextDouble() * 400 - 200,
                r.nextDouble() * 100, r.nextBoolean() ? null : r.nextDouble() * 100,
                r.nextInt(10), r.nextInt(5), r.nextInt(171), r.nextInt(30));
    }

    private static double total(Input in) {
        return MonthlyScore.score(in).total();
    }

    @Test
    void everyComponentIsMonotoneAndBounded() {
        Random r = new Random(11);
        for (int i = 0; i < 20_000; i++) {
            Input in = random(r);
            var b = MonthlyScore.score(in);
            assertThat(b.total()).isCloseTo(b.rating() + b.activity() + b.wins() + b.improvement() + b.highlights() + b.kudos(),
                    org.assertj.core.api.Assertions.within(0.31));
            assertThat(b.rating()).isBetween(MonthlyScore.RATING_FLOOR, MonthlyScore.RATING_CAP);
            assertThat(b.activity()).isBetween(0.0, MonthlyScore.MATCH_CAP * MonthlyScore.POINTS_PER_MATCH);
            assertThat(b.improvement()).isBetween(MonthlyScore.AVG_MIN, MonthlyScore.AVG_MAX);
            assertThat(b.kudos()).isBetween(0.0, (double) MonthlyScore.KUDOS_CAP);
            assertThat(Double.isFinite(b.total())).isTrue();

            // one more match won (played + won) never lowers the total (with 0 matches there is no month average yet)
            if (in.matches() > 0) {
            Input moreWins = new Input(in.matches() + 1, in.wins() + 1, in.ratingChange(), in.monthAverage(), in.priorAverage(),
                    in.count180(), in.tonCheckouts(), in.highestCheckout(), in.kudos());
            assertThat(total(moreWins)).isGreaterThanOrEqualTo(total(in));
            }
            // a 180, a ton-plus checkout, a kudos or more rating never lower it either
            assertThat(total(new Input(in.matches(), in.wins(), in.ratingChange(), in.monthAverage(), in.priorAverage(),
                    in.count180() + 1, in.tonCheckouts(), in.highestCheckout(), in.kudos()))).isGreaterThan(total(in));
            assertThat(total(new Input(in.matches(), in.wins(), in.ratingChange(), in.monthAverage(), in.priorAverage(),
                    in.count180(), in.tonCheckouts() + 1, in.highestCheckout(), in.kudos()))).isGreaterThan(total(in));
            assertThat(total(new Input(in.matches(), in.wins(), in.ratingChange(), in.monthAverage(), in.priorAverage(),
                    in.count180(), in.tonCheckouts(), in.highestCheckout(), in.kudos() + 1))).isGreaterThanOrEqualTo(total(in));
            if (in.ratingChange() != null) {
                assertThat(total(new Input(in.matches(), in.wins(), in.ratingChange() + 5, in.monthAverage(), in.priorAverage(),
                        in.count180(), in.tonCheckouts(), in.highestCheckout(), in.kudos()))).isGreaterThanOrEqualTo(total(in));
            }
        }
    }

    @Test
    void zeroMatchesMeansNoImprovementPointsEvenWithAnOldAverage() {
        var b = MonthlyScore.score(new Input(0, 0, null, 0, 60.0, 0, 0, 0, 0));
        assertThat(b.improvement()).isZero();
        assertThat(b.total()).isZero();
    }

    private static AwardService.Standing row(int rank, long id, boolean eligible, Input in) {
        return new AwardService.Standing(rank, new PlayerSummary(id, "u" + id, "User " + id, 1500, 100, false, false), eligible, in, MonthlyScore.score(in));
    }

    @Test
    void tiesGoToTheBetterRankedPlayerAndNobodyWinsWithZero() {
        var first = row(1, 1, true, new Input(5, 3, 10.0, 50, 45.0, 2, 0, 100, 0));
        var second = row(2, 2, true, new Input(5, 2, 10.0, 50, 45.0, 2, 0, 100, 0));
        Map<Award, AwardService.Standing> won = AwardService.decide(List.of(first, second));
        assertThat(won.get(Award.MOST_IMPROVED)).isEqualTo(first);
        assertThat(won.get(Award.MOST_ACTIVE)).isEqualTo(first);
        assertThat(won.get(Award.MOST_180S)).isEqualTo(first);
        assertThat(won.get(Award.HIGHEST_CHECKOUT)).isEqualTo(first);

        var quiet = row(1, 3, true, new Input(3, 0, -10.0, 30, 30.0, 0, 0, 0, 0));
        Map<Award, AwardService.Standing> none = AwardService.decide(List.of(quiet));
        assertThat(none).containsKeys(Award.PLAYER_OF_MONTH, Award.MOST_ACTIVE)
                .doesNotContainKeys(Award.MOST_IMPROVED, Award.MOST_180S, Award.HIGHEST_CHECKOUT);
        assertThat(AwardService.decide(List.of())).isEmpty();
    }

    @Test
    void ineligiblePlayersCanNeverTakeTheMainOrActivityAwards() {
        Random r = new Random(5);
        for (int i = 0; i < 2000; i++) {
            List<AwardService.Standing> rows = new ArrayList<>();
            for (int p = 0; p < 6; p++) rows.add(row(p + 1, p, r.nextBoolean(), random(r)));
            Map<Award, AwardService.Standing> won = AwardService.decide(rows);
            for (Award a : List.of(Award.PLAYER_OF_MONTH, Award.MOST_IMPROVED, Award.MOST_ACTIVE)) {
                if (won.containsKey(a)) assertThat(won.get(a).eligible()).as(a.name()).isTrue();
            }
            if (won.containsKey(Award.MOST_IMPROVED)) assertThat(won.get(Award.MOST_IMPROVED).stats().ratingChange()).isPositive();
        }
    }
}
