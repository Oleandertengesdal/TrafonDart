package no.trafondart.awards;

import no.trafondart.awards.AwardEntities.Award;
import no.trafondart.user.UserDtos.PlayerSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyScoreTest {

    @Test
    void improvingActivePlayerBeatsStrongerButStagnantPlayer() {
        // Casual player: 6 matches, 2 wins, +30 rating, average up 5 on their own baseline.
        var improver = MonthlyScore.score(new MonthlyScore.Input(6, 2, 30.0, 55.0, 50.0, 0, 0, 40, 0));
        // Strongest player: 6 wins out of 6, but already rated high so only +10, same average as always.
        var champion = MonthlyScore.score(new MonthlyScore.Input(6, 6, 10.0, 80.0, 80.0, 0, 0, 60, 0));
        assertThat(improver.total()).isEqualTo(30 + 24 + 12 + 10);
        assertThat(champion.total()).isEqualTo(10 + 24 + 36);
        assertThat(improver.total()).isGreaterThan(champion.total());
    }

    @Test
    void capsAndFloorsKeepItFair() {
        var b = MonthlyScore.score(new MonthlyScore.Input(40, 0, -200.0, 20.0, 60.0, 2, 1, 120, 50));
        assertThat(b.rating()).isEqualTo(-25);          // floor
        assertThat(b.activity()).isEqualTo(20 * 4);     // 20-match cap
        assertThat(b.improvement()).isEqualTo(-20);     // floor
        assertThat(b.highlights()).isEqualTo(2 * 3 + 5);
        assertThat(b.kudos()).isEqualTo(10);            // cap

        // A brand-new player's provisional rating can jump hundreds of points; only 60 of them count.
        var rookie = MonthlyScore.score(new MonthlyScore.Input(4, 4, 340.0, 50.0, null, 0, 0, 0, 0));
        assertThat(rookie.rating()).isEqualTo(60);
    }

    @Test
    void newPlayersGetNoImprovementOrRatingPointsYet() {
        var b = MonthlyScore.score(new MonthlyScore.Input(3, 1, null, 45.0, null, 0, 0, 0, 0));
        assertThat(b.rating()).isZero();
        assertThat(b.improvement()).isZero();
        assertThat(b.total()).isEqualTo(3 * 4 + 6);
    }

    private static AwardService.Standing row(int rank, long id, boolean eligible, MonthlyScore.Input in) {
        return new AwardService.Standing(rank, new PlayerSummary(id, "u" + id, "User " + id, 1500, 100, false, false), eligible, in, MonthlyScore.score(in));
    }

    @Test
    void categoriesOnlyAwardedWhenEarned() {
        var a = row(1, 1, true, new MonthlyScore.Input(5, 3, 25.0, 60, 55.0, 0, 0, 80, 0));
        var b = row(2, 2, true, new MonthlyScore.Input(8, 2, -5.0, 50, 50.0, 2, 0, 40, 0));
        var c = row(3, 3, false, new MonthlyScore.Input(1, 1, 12.0, 70, 70.0, 0, 1, 121, 0));
        Map<Award, AwardService.Standing> won = AwardService.decide(List.of(a, b, c));
        assertThat(won.get(Award.PLAYER_OF_MONTH)).isEqualTo(a);
        assertThat(won.get(Award.MOST_IMPROVED)).isEqualTo(a);
        assertThat(won.get(Award.MOST_ACTIVE)).isEqualTo(b);
        assertThat(won.get(Award.MOST_180S)).isEqualTo(b);
        // Highlights count for everyone, even below the match minimum.
        assertThat(won.get(Award.HIGHEST_CHECKOUT)).isEqualTo(c);

        var nobodyImproved = AwardService.decide(List.of(b));
        assertThat(nobodyImproved).doesNotContainKey(Award.MOST_IMPROVED);
        assertThat(AwardService.decide(List.of(c))).doesNotContainKey(Award.PLAYER_OF_MONTH);
    }
}
