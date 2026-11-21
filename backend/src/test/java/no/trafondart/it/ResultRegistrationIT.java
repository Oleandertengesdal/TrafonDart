package no.trafondart.it;

import no.trafondart.it.ApiClient.Raw;
import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registering only the result of a normal match – no darts. It waits for confirmation like any other match, then
 * counts for wins, legs, rating, streaks and head-to-head, but never for the throwing statistics.
 */
class ResultRegistrationIT extends IntegrationTestBase {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    static Map<String, Object> resultBody(long p1, long p2, int legsToWin, int legs1, int legs2, boolean rated, LocalDate playedOn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("player1Id", p1);
        m.put("player2Id", p2);
        m.put("startScore", 501);
        m.put("inRule", "STRAIGHT");
        m.put("outRule", "DOUBLE");
        m.put("legsToWin", legsToWin);
        m.put("legsWon", List.of(legs1, legs2));
        m.put("rated", rated);
        if (playedOn != null) m.put("playedOn", playedOn.toString());
        return m;
    }

    private static LocalDate today() {
        return LocalDate.now(OSLO);
    }

    @Test
    void aRegisteredResultWaitsForConfirmationThenCountsForEverythingButThrowing() {
        Member a = member("rrA"), b = member("rrB");
        Response r = a.client().post("/api/matches/results", resultBody(a.id(), b.id(), 2, 2, 1, true, null));
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        JsonNode m = r.body();
        String id = m.get("id").asString();
        assertThat(m.get("status").asString()).isEqualTo("UNCONFIRMED");
        assertThat(m.get("resultOnly").asBoolean()).isTrue();
        assertThat(m.get("dartCount").asInt()).isZero();
        assertThat(m.get("winnerIndex").asInt()).isZero();
        assertThat(m.get("legsWon").get(0).asInt()).isEqualTo(2);
        assertThat(m.get("legsWon").get(1).asInt()).isEqualTo(1);
        assertThat(m.get("confirmation").get("registeredBy").asString()).isEqualTo(a.username());
        assertThat(m.get("config").get("startScore").asInt()).isEqualTo(501);

        // a result can't be scored
        Response dart = throwDart(a.client(), id, 20, 3, 0);
        assertThat(dart.status()).isEqualTo(409);
        assertThat(dart.code()).isEqualTo("MATCH_NOT_IN_PROGRESS");

        JsonNode row = a.client().get("/api/matches/recent?user=" + a.username()).body().get(0);
        assertThat(row.get("resultOnly").asBoolean()).isTrue();
        assertThat(row.get("legsWon").get(0).asInt()).isEqualTo(2);
        assertThat(a.client().get("/api/users/" + a.username() + "/stats").body().get("matchesPlayed").asInt()).isZero();

        assertThat(b.client().post("/api/matches/" + id + "/confirm", null).status()).isEqualTo(200);

        JsonNode stats = a.client().get("/api/users/" + a.username() + "/stats").body();
        assertThat(stats.get("matchesPlayed").asInt()).isEqualTo(1);
        assertThat(stats.get("matchesWon").asInt()).isEqualTo(1);
        assertThat(stats.get("legsWon").asInt()).isEqualTo(2);
        assertThat(stats.get("legsLost").asInt()).isEqualTo(1);
        assertThat(stats.get("player").get("rating").asInt()).isGreaterThan(1500);
        assertThat(stats.get("currentStreak").asInt()).isEqualTo(1);
        // no darts: no average, and no point at 0 on the average chart
        assertThat(stats.get("average").asDouble()).isZero();
        assertThat(stats.get("averageTrend")).isEmpty();
        assertThat(a.client().get("/api/users/" + a.username() + "/head-to-head").body().get(0).get("won").asInt()).isEqualTo(1);

        // every screen built from matches still works, with clean numbers
        for (String path : new String[]{"/api/matches/" + id, "/api/matches/" + id + "/breakdown",
                "/api/users/" + a.username() + "/dart-stats", "/api/users/" + a.username() + "/achievements",
                "/api/users/" + a.username() + "/recap", "/api/users/" + b.username() + "/recap", "/api/awards/current"}) {
            Raw raw = a.client().raw("GET", path, null, null);
            assertThat(raw.status()).as(path).isEqualTo(200);
            assertThat(raw.body()).as(path).doesNotContain("NaN", "Infinity");
        }
        JsonNode darts = a.client().get("/api/users/" + a.username() + "/dart-stats").body();
        assertThat(darts.get("dartsThrown").asInt()).isZero();
        assertThat(darts.get("legsPlayed").asInt()).as("a result has no legs to analyse").isZero();
        JsonNode recap = a.client().get("/api/users/" + a.username() + "/recap").body();
        assertThat(recap.get("bestMatch").isNull()).as("no average, so no 'best match by average'").isTrue();
    }

    @Test
    void thePlayedDateCanBeUpToAWeekBack() {
        Member a = member("rrdA"), b = member("rrdB");
        LocalDate threeDaysAgo = today().minusDays(3);
        Response r = a.client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 0, 1, false, threeDaysAgo));
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        assertThat(r.body().get("winnerIndex").asInt()).isEqualTo(1);
        LocalDate completed = java.time.Instant.parse(r.body().get("completedAt").asString()).atZone(OSLO).toLocalDate();
        assertThat(completed).isEqualTo(threeDaysAgo);
        // a week back is fine, more is not; tomorrow is not
        assertThat(a.client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, false, today().minusDays(7))).status()).isEqualTo(201);
        assertThat(a.client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, false, today().minusDays(8))).code()).isEqualTo("VALIDATION_FAILED");
        assertThat(a.client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, false, today().plusDays(1))).code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void theScoreMustBeAFinishedMatch() {
        Member a = member("rrvA"), b = member("rrvB");
        int[][] bad = {{2, 2}, {1, 0}, {3, 0}, {2, 3}, {-1, 2}, {0, 0}};
        for (int[] legs : bad) {
            Response r = a.client().post("/api/matches/results", resultBody(a.id(), b.id(), 2, legs[0], legs[1], false, null));
            assertThat(r.status()).as("legs %d–%d", legs[0], legs[1]).isEqualTo(400);
        }
        assertThat(a.client().post("/api/matches/results", resultBody(a.id(), a.id(), 1, 1, 0, false, null)).code()).isEqualTo("MATCH_PLAYERS_SAME");
        Map<String, Object> wrongGame = resultBody(a.id(), b.id(), 1, 1, 0, false, null);
        wrongGame.put("startScore", 400);
        assertThat(a.client().post("/api/matches/results", wrongGame).status()).isEqualTo(400);
        Map<String, Object> noLegs = resultBody(a.id(), b.id(), 1, 1, 0, false, null);
        noLegs.put("legsWon", List.of(1));
        assertThat(a.client().post("/api/matches/results", noLegs).status()).isEqualTo(400);
        Map<String, Object> badDate = resultBody(a.id(), b.id(), 1, 1, 0, false, null);
        badDate.put("playedOn", "yesterday");
        assertThat(a.client().post("/api/matches/results", badDate).status()).isEqualTo(400);
    }

    @Test
    void onlyAPlayerOrAnAdminCanRegisterARatedResult() {
        Member a = member("rrpA"), b = member("rrpB"), outsider = member("rrpO");
        Response rated = outsider.client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, true, null));
        assertThat(rated.status()).isEqualTo(403);
        // a casual result for friends is fine, and then either of them confirms it
        Response casual = outsider.client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, false, null));
        assertThat(casual.status()).isEqualTo(201);
        assertThat(casual.body().get("confirmation").get("confirmerIds")).hasSize(2);
        assertThat(admin.post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, true, null)).status()).isEqualTo(201);
        assertThat(client().post("/api/matches/results", resultBody(a.id(), b.id(), 1, 1, 0, false, null)).status()).isEqualTo(401);
    }
}
