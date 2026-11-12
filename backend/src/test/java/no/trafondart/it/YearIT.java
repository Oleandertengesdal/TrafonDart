package no.trafondart.it;

import no.trafondart.awards.AwardService;
import no.trafondart.awards.YearAwardService;
import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Player of the Year and the year recap. */
class YearIT extends IntegrationTestBase {

    @Autowired
    AwardService awards;

    @Autowired
    YearAwardService yearAwards;

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode standing(JsonNode race, String username) {
        for (JsonNode s : race.get("standings")) if (s.get("player").get("username").asString().equals(username)) return s;
        return null;
    }

    @Test
    void theYearRaceAddsUpMonthPoints() {
        Member a = member("yrA"), b = member("yrB");
        finishedMatch(a.client(), a.id(), b.id(), true, 0);
        finishedMatch(a.client(), a.id(), b.id(), true, 0);
        JsonNode race = a.client().get("/api/awards/year/current").body();
        assertThat(race.get("year").asInt()).isEqualTo(awards.currentMonth().getYear());
        JsonNode mine = standing(race, a.username());
        assertThat(mine).isNotNull();
        assertThat(mine.get("matches").asInt()).isEqualTo(2);
        // the year's points are the sum of this month's points (only one month played here)
        double monthPoints = 0;
        for (JsonNode s : a.client().get("/api/awards/current").body().get("standings")) {
            if (s.get("player").get("username").asString().equals(a.username())) monthPoints = s.get("points").get("total").asDouble();
        }
        assertThat(mine.get("points").asDouble()).isCloseTo(monthPoints, org.assertj.core.api.Assertions.within(0.11));
        assertThat(race.get("minMatches").asInt()).isPositive();
    }

    @Test
    void aFinishedYearCrownsAPlayerOfTheYearWithABadge() {
        Member a = member("poyA"), b = member("poyB");
        String id = finishedMatch(a.client(), a.id(), b.id(), true, 0).get("id").asString();
        // move the match into an old, finished year nobody else played in
        jdbc.update("update matches set completed_at = ? where id = ?", Timestamp.from(Instant.parse("2019-06-15T18:00:00Z")), UUID.fromString(id));
        jdbc.update("update rating_history set rated_at = ? where match_id = ?", Timestamp.from(Instant.parse("2019-06-15T18:00:00Z")), UUID.fromString(id));
        yearAwards.finalizeYear(2019);
        JsonNode years = a.client().get("/api/awards/years").body();
        JsonNode y2019 = null;
        for (JsonNode y : years) if (y.get("year").asInt() == 2019) y2019 = y;
        assertThat(y2019).isNotNull();
        assertThat(y2019.get("award").get("player").get("username").asString()).isEqualTo(a.username());
        JsonNode badges = a.client().get("/api/users/" + a.username() + "/awards").body();
        assertThat(badges.toString()).contains("PLAYER_OF_YEAR").contains("\"period\":\"2019\"");
        // idempotent
        yearAwards.finalizeYear(2019);
        int count = 0;
        for (JsonNode y : a.client().get("/api/awards/years").body()) if (y.get("year").asInt() == 2019) count++;
        assertThat(count).isEqualTo(1);
        // voiding the match re-decides the year
        long did = b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "practice")).body().get("id").asLong();
        admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "VOID_MATCH"));
        yearAwards.ensureFinalized();
        assertThat(a.client().get("/api/users/" + a.username() + "/awards").body().toString()).doesNotContain("\"period\":\"2019\"");
    }

    @Test
    void theRecapTellsTheStoryOfTheYear() {
        Member a = member("rcA"), b = member("rcB"), c = member("rcC");
        finishedMatch(a.client(), a.id(), b.id(), true, 0);
        finishedMatch(a.client(), a.id(), b.id(), true, 1);
        finishedMatch(a.client(), a.id(), b.id(), true, 1);
        finishedMatch(a.client(), a.id(), c.id(), true, 0);
        finishedMatch(a.client(), a.id(), c.id(), true, 0);
        int year = Year.now().getValue();
        Response r = a.client().get("/api/users/" + a.username() + "/recap?year=" + year);
        assertThat(r.status()).isEqualTo(200);
        JsonNode recap = r.body();
        assertThat(recap.get("year").asInt()).isEqualTo(year);
        assertThat(recap.get("soFar").asBoolean()).isTrue();
        assertThat(recap.get("matches").get("played").asInt()).isEqualTo(5);
        assertThat(recap.get("matches").get("won").asInt()).isEqualTo(3);
        assertThat(recap.get("dartsThrown").asInt()).isPositive();
        assertThat(recap.get("nemesis").get("player").get("username").asString()).isEqualTo(b.username());
        assertThat(recap.get("favouriteVictim").get("player").get("username").asString()).isEqualTo(c.username());
        assertThat(recap.get("longestWinStreak").asInt()).isEqualTo(2);
        assertThat(recap.get("bestMatch").get("matchId").asString()).isNotBlank();
        assertThat(recap.get("favouriteFinish").get("label").asString()).isNotBlank();
        assertThat(recap.get("busiestWeekday").asInt()).isBetween(1, 7);
        assertThat(recap.toString()).doesNotContain("NaN", "Infinity");

        JsonNode empty = a.client().get("/api/users/" + a.username() + "/recap?year=2001").body();
        assertThat(empty.get("matches").get("played").asInt()).isZero();
        assertThat(empty.get("nemesis").isNull()).isTrue();
        assertThat(a.client().get("/api/users/" + a.username() + "/recap?year=1999").status()).isEqualTo(400);
        assertThat(a.client().get("/api/users/" + a.username() + "/recap?year=" + (year + 1)).status()).isEqualTo(400);
        assertThat(a.client().get("/api/users/nobody_here/recap?year=" + year).status()).isEqualTo(404);
    }
}
