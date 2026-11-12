package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Creating, scoring, undoing, finishing, abandoning and voiding matches – and what that does to the numbers. */
class MatchFlowIT extends IntegrationTestBase {

    @Test
    void matchSettingsAreValidated() {
        Member a = member("cfgA"), b = member("cfgB");
        ApiClient c = a.client();
        assertThat(c.post("/api/matches", matchBody(a.id(), a.id(), true, 501, "DOUBLE", 1)).code()).isEqualTo("MATCH_PLAYERS_SAME");
        assertThat(c.post("/api/matches", matchBody(a.id(), b.id(), true, 500, "DOUBLE", 1)).status()).isEqualTo(400);
        assertThat(c.post("/api/matches", matchBody(a.id(), b.id(), true, 501, "DOUBLE", 0)).status()).isEqualTo(400);
        assertThat(c.post("/api/matches", matchBody(a.id(), b.id(), true, 501, "DOUBLE", 12)).status()).isEqualTo(400);
        assertThat(c.post("/api/matches", matchBody(a.id(), b.id(), true, 501, "TRIPLE", 1)).status()).isEqualTo(400);
        assertThat(c.post("/api/matches", matchBody(a.id(), 99_999_999L, true, 501, "DOUBLE", 1)).status()).isEqualTo(400);
        Map<String, Object> starter = matchBody(a.id(), b.id(), true, 501, "DOUBLE", 1);
        starter.put("startingPlayer", 2);
        assertThat(c.post("/api/matches", starter).status()).isEqualTo(400);
        starter.put("startingPlayer", null);
        Response random = c.post("/api/matches", starter);
        assertThat(random.status()).isEqualTo(201);
        assertThat(random.body().get("currentPlayerIndex").asInt()).isIn(0, 1);
        c.post("/api/matches/" + random.body().get("id").asString() + "/abandon", null);
    }

    @Test
    void impossibleDartsAreRejectedAndNothingIsRecorded() {
        Member a = member("dartA"), b = member("dartB");
        String id = quickMatch(a.client(), a.id(), b.id(), false);
        int[][] bad = {{21, 1}, {25, 3}, {0, 1}, {20, 0}, {-1, 1}, {20, 4}, {26, 1}};
        for (int[] d : bad) {
            Response r = throwDart(a.client(), id, d[0], d[1], 0);
            assertThat(r.status()).as("%d x %d", d[0], d[1]).isEqualTo(400);
        }
        assertThat(a.client().post("/api/matches/" + id + "/throws", Map.of("segment", 20, "multiplier", 1)).status()).isEqualTo(400);
        assertThat(a.client().get("/api/matches/" + id).body().get("dartCount").asInt()).isZero();
    }

    @Test
    void twoTabletsSendingTheSameDartRecordItOnce() throws Exception {
        Member a = member("raceA"), b = member("raceB");
        String id = quickMatch(a.client(), a.id(), b.id(), false);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ApiClient scorer = i % 2 == 0 ? a.client() : b.client();
            results.add(pool.submit(() -> throwDart(scorer, id, 20, 3, 0)));
        }
        int ok = 0;
        for (Future<Response> f : results) {
            Response r = f.get();
            if (r.status() == 200) ok++;
            else assertThat(r.code()).isEqualTo("MATCH_OUT_OF_SYNC");
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(a.client().get("/api/matches/" + id).body().get("dartCount").asInt()).isEqualTo(1);
    }

    @Test
    void undoWorksAcrossTurnsAndLegsAndNeverPastTheStart() {
        Member a = member("undoA"), b = member("undoB");
        Response created = a.client().post("/api/matches", matchBody(a.id(), b.id(), false, 301, "STRAIGHT", 2));
        String id = created.body().get("id").asString();
        assertThat(a.client().post("/api/matches/" + id + "/undo", Map.of("expectedDartCount", 1)).code()).isEqualTo("MATCH_NOTHING_TO_UNDO");
        // win leg 1 for player 0 quickly: T20 T20 T20 | miss x3 | T20 T20 S1
        int n = 0;
        for (int[] d : new int[][]{{20, 3}, {20, 3}, {20, 3}, {0, 0}, {0, 0}, {0, 0}, {20, 3}, {20, 3}, {1, 1}}) {
            assertThat(throwDart(a.client(), id, d[0], d[1], n++).status()).isEqualTo(200);
        }
        JsonNode afterLeg = a.client().get("/api/matches/" + id).body();
        assertThat(afterLeg.get("legsWon").get(0).asInt()).isEqualTo(1);
        assertThat(afterLeg.get("currentLeg").asInt()).isEqualTo(2);
        // undo the leg-winning dart: we're back in leg 1 with 1 left
        JsonNode undone = a.client().post("/api/matches/" + id + "/undo", Map.of("expectedDartCount", n)).body();
        assertThat(undone.get("legsWon").get(0).asInt()).isZero();
        assertThat(undone.get("currentLeg").asInt()).isEqualTo(1);
        assertThat(undone.get("remaining").get(0).asInt()).isEqualTo(1);
        // a stale undo is refused
        assertThat(a.client().post("/api/matches/" + id + "/undo", Map.of("expectedDartCount", n)).code()).isEqualTo("MATCH_OUT_OF_SYNC");
        assertThat(a.client().post("/api/matches/" + id + "/undo", Map.of("expectedDartCount", 0)).status()).isEqualTo(400);
    }

    @Test
    void finishedMatchStatisticsAddUpFromTheDarts() {
        Member a = member("sumA"), b = member("sumB");
        Response created = a.client().post("/api/matches", matchBody(a.id(), b.id(), true, 501, "DOUBLE", 2));
        String id = created.body().get("id").asString();
        JsonNode end = playToEnd(a.client(), id, 0);
        assertThat(end.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(end.get("winnerIndex").asInt()).isZero();
        assertThat(end.get("legsWon").get(0).asInt()).isEqualTo(2);

        JsonNode breakdown = a.client().get("/api/matches/" + id + "/breakdown").body();
        int[] scored = new int[2];
        int[] dartsBy = new int[2];
        for (JsonNode leg : breakdown.get("legs")) {
            for (JsonNode turn : leg.get("turns")) {
                int p = turn.get("playerIndex").asInt();
                scored[p] += turn.get("scored").asInt();
                dartsBy[p] += turn.get("darts").size();
            }
        }
        assertThat(scored[0]).as("winner scored exactly two legs of 501").isEqualTo(1002);
        assertThat(scored[1]).isZero();

        JsonNode profile = a.client().get("/api/users/" + a.username() + "/stats").body();
        assertThat(profile.get("matchesPlayed").asInt()).isEqualTo(1);
        assertThat(profile.get("legsWon").asInt()).isEqualTo(2);
        assertThat(profile.get("average").asDouble()).isCloseTo(1002 * 3.0 / dartsBy[0], org.assertj.core.api.Assertions.within(0.01));
        JsonNode loser = a.client().get("/api/users/" + b.username() + "/stats").body();
        assertThat(loser.get("matchesLost").asInt()).isEqualTo(1);
        assertThat(loser.get("player").get("rating").asInt()).isLessThan(1500);
        assertThat(profile.get("player").get("rating").asInt()).isGreaterThan(1500);

        JsonNode h2h = a.client().get("/api/users/" + a.username() + "/head-to-head/" + b.username()).body();
        assertThat(h2h.get("played").asInt()).isEqualTo(1);
        assertThat(h2h.get("won").asInt()).isEqualTo(1);
        JsonNode h2hBack = a.client().get("/api/users/" + b.username() + "/head-to-head/" + a.username()).body();
        assertThat(h2hBack.get("won").asInt()).isZero();
        assertThat(h2hBack.get("lost").asInt()).isEqualTo(1);
    }

    @Test
    void abandonedMatchesLeaveNoTraceInStatsOrRatings() {
        Member a = member("abnA"), b = member("abnB");
        String id = quickMatch(a.client(), a.id(), b.id(), true);
        throwDart(a.client(), id, 20, 3, 0);
        JsonNode r = a.client().post("/api/matches/" + id + "/abandon", null).body();
        assertThat(r.get("status").asString()).isEqualTo("ABANDONED");
        assertThat(throwDart(a.client(), id, 20, 3, 1).code()).isEqualTo("MATCH_NOT_IN_PROGRESS");
        assertThat(a.client().post("/api/matches/" + id + "/abandon", null).code()).isEqualTo("MATCH_NOT_IN_PROGRESS");
        JsonNode stats = a.client().get("/api/users/" + a.username() + "/stats").body();
        assertThat(stats.get("matchesPlayed").asInt()).isZero();
        assertThat(stats.get("player").get("rating").asInt()).isEqualTo(1500);
        assertThat(a.client().get("/api/matches/live").body().toString()).doesNotContain(id);
        assertThat(a.client().get("/api/matches/recent?user=" + a.username()).body().toString()).doesNotContain(id);
    }

    @Test
    void voidingAMatchMakesRatingsExactlyAsIfItNeverHappened() {
        Member a = member("voidA"), b = member("voidB"), c = member("voidC");
        finishedMatch(a.client(), a.id(), b.id(), true, 0);
        int ratingAfterFirst = a.client().get("/api/users/" + a.username() + "/stats").body().get("player").get("rating").asInt();
        String disputed = finishedMatch(a.client(), a.id(), c.id(), true, 1).get("id").asString();
        finishedMatch(b.client(), b.id(), c.id(), true, 0);

        Response dispute = c.client().post("/api/matches/" + disputed + "/disputes", Map.of("reason", "Scored on the wrong board"));
        assertThat(dispute.status()).isEqualTo(201);
        long did = dispute.body().get("id").asLong();
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "VOID_MATCH")).status()).isEqualTo(200);
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "REJECT")).code()).isEqualTo("CONFLICT");

        JsonNode aStats = a.client().get("/api/users/" + a.username() + "/stats").body();
        assertThat(aStats.get("matchesPlayed").asInt()).isEqualTo(1);
        assertThat(aStats.get("player").get("rating").asInt()).isEqualTo(ratingAfterFirst);
        JsonNode cStats = a.client().get("/api/users/" + c.username() + "/stats").body();
        assertThat(cStats.get("matchesPlayed").asInt()).isEqualTo(1);
        assertThat(cStats.get("matchesWon").asInt()).isZero();
        assertThat(a.client().get("/api/users/" + a.username() + "/head-to-head/" + c.username()).body().get("played").asInt()).isZero();
        // the voided match still shows in history, marked as voided
        assertThat(a.client().get("/api/matches/" + disputed).body().get("status").asString()).isEqualTo("VOIDED");
        // and can't be disputed again
        assertThat(c.client().post("/api/matches/" + disputed + "/disputes", Map.of("reason", "again")).code()).isEqualTo("DISPUTE_NOT_ALLOWED");
    }

    @Test
    void disputesAreValidatedAndOnlyOneCanBeOpen() {
        Member a = member("dspA"), b = member("dspB");
        String live = quickMatch(a.client(), a.id(), b.id(), true);
        assertThat(b.client().post("/api/matches/" + live + "/disputes", Map.of("reason", "not finished")).code()).isEqualTo("DISPUTE_NOT_ALLOWED");
        String id = playToEnd(a.client(), live, 0).get("id").asString();
        assertThat(b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", " ")).status()).isEqualTo(400);
        assertThat(b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "x".repeat(501))).status()).isEqualTo(400);
        assertThat(b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "bad\u0000byte")).status()).isEqualTo(400);
        assertThat(b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "Wrong double")).status()).isEqualTo(201);
        assertThat(a.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "Me too")).code()).isEqualTo("DISPUTE_ALREADY_OPEN");
        long did = admin.get("/api/admin/disputes").body().get(0).get("id").asLong();
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "MAYBE")).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "REJECT", "note", "evil\u0000")).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/disputes/987654/resolve", Map.of("outcome", "REJECT")).status()).isEqualTo(404);
    }

    @Test
    void lookupsOfThingsThatDoNotExistAre404AndBadIdsAre400() {
        Member m = member("lookup");
        assertThat(m.client().get("/api/matches/" + UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(m.client().get("/api/matches/not-a-uuid").status()).isEqualTo(400);
        assertThat(m.client().get("/api/users/nobody_at_all/stats").status()).isEqualTo(404);
        assertThat(m.client().get("/api/matches/recent?user=nobody_at_all").status()).isEqualTo(404);
        assertThat(m.client().get("/api/matches/recent?limit=abc").status()).isEqualTo(400);
        assertThat(m.client().get("/api/matches/recent?limit=-5").status()).isEqualTo(200);
        assertThat(m.client().get("/api/matches/recent?limit=100000").body().size()).isLessThanOrEqualTo(100);
        assertThat(m.client().get("/api/users/%27%20OR%20%271%27%3D%271/stats").status()).isEqualTo(404);
    }
}
