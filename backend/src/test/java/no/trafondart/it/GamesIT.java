package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Party games over the real API: set-up rules, scoring, live updates, results and what they never touch. */
class GamesIT extends IntegrationTestBase {

    private Map<String, Object> game(String type, List<Long> players, boolean teams, Map<String, Object> settings) {
        Map<String, Object> b = new HashMap<>();
        b.put("type", type);
        b.put("playerIds", players);
        b.put("teams", teams);
        b.put("settings", settings);
        return b;
    }

    private JsonNode create(ApiClient c, Map<String, Object> body) {
        Response r = c.post("/api/games", body);
        assertThat(r.status()).as("%s", r.body()).isEqualTo(201);
        return r.body();
    }

    private Response dart(ApiClient c, String id, int seg, int mult, int expected) {
        return c.post("/api/games/" + id + "/throws", Map.of("segment", seg, "multiplier", mult, "expectedDartCount", expected));
    }

    @Test
    void aSoloAroundTheClockRunsFromStartToFinish() {
        Member m = member("atcSolo");
        JsonNode g = create(m.client(), game("AROUND_THE_CLOCK", List.of(m.id()), false, Map.of("multiplierSkips", false)));
        String id = g.get("id").asString();
        assertThat(g.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(g.get("rated").asBoolean()).isFalse();
        int n = 0;
        for (int target = 1; target <= 20; target++) assertThat(dart(m.client(), id, target, 1, n++).status()).isEqualTo(200);
        JsonNode done = dart(m.client(), id, 25, 2, n).body();
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("winnerSide").asInt()).isZero();
        assertThat(done.get("slots").get(0).get("darts").asInt()).isEqualTo(21);
        assertThat(dart(m.client(), id, 1, 1, n + 1).code()).isEqualTo("MATCH_NOT_IN_PROGRESS");
    }

    @Test
    void doublesX01SharesScoresAndRecordsResultsForAllFour() {
        Member a1 = member("dA1"), b1 = member("dB1"), a2 = member("dA2"), b2 = member("dB2");
        // teams are given as [a1, a2] vs [b1, b2]; the throwing order alternates A1, B1, A2, B2
        JsonNode g = create(a1.client(), game("X01", List.of(a1.id(), a2.id(), b1.id(), b2.id()), true,
                Map.of("startScore", 301, "inRule", "STRAIGHT", "outRule", "STRAIGHT", "legsToWin", 1)));
        String id = g.get("id").asString();
        assertThat(g.get("players").get(0).get("player").get("id").asLong()).isEqualTo(a1.id());
        assertThat(g.get("players").get(1).get("player").get("id").asLong()).isEqualTo(b1.id());
        assertThat(g.get("players").get(2).get("player").get("id").asLong()).isEqualTo(a2.id());
        assertThat(g.get("players").get(1).get("side").asInt()).isEqualTo(1);

        int n = 0;
        int[][] seq = {{20, 3}, {20, 3}, {20, 3}, {0, 0}, {0, 0}, {0, 0}, {20, 3}, {20, 3}, {1, 1}};
        JsonNode last = null;
        for (int[] d : seq) last = dart(b2.client(), id, d[0], d[1], n++).body();
        assertThat(last.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(last.get("winnerSide").asInt()).isZero();

        JsonNode statsA2 = a2.client().get("/api/users/" + a2.username() + "/game-stats").body();
        JsonNode x01 = null;
        for (JsonNode t : statsA2.get("types")) if (t.get("type").asString().equals("X01")) x01 = t;
        assertThat(x01.get("played").asInt()).isEqualTo(1);
        assertThat(x01.get("won").asInt()).isEqualTo(1);
        JsonNode statsB1 = b1.client().get("/api/users/" + b1.username() + "/game-stats").body();
        for (JsonNode t : statsB1.get("types")) if (t.get("type").asString().equals("X01")) assertThat(t.get("won").asInt()).isZero();
        // never touches ratings
        assertThat(a1.client().get("/api/users/" + a1.username() + "/stats").body().get("player").get("rating").asInt()).isEqualTo(1500);
        assertThat(a1.client().get("/api/users/" + a1.username() + "/stats").body().get("matchesPlayed").asInt()).isZero();
    }

    @Test
    void killerNumbersAreAssignedByTheServerAndAreDistinct() {
        List<Long> ids = new ArrayList<>();
        Member first = member("kil0");
        ids.add(first.id());
        for (int i = 1; i < 6; i++) ids.add(member("kil" + i).id());
        Map<String, Object> settings = new HashMap<>();
        settings.put("lives", 3);
        settings.put("killerNumbers", List.of(1, 1, 1, 1, 1, 1)); // ignored: the server draws the numbers
        JsonNode g = create(first.client(), game("KILLER", ids, false, settings));
        List<Integer> numbers = new ArrayList<>();
        for (JsonNode side : g.get("sides")) numbers.add(side.get("number").asInt());
        assertThat(numbers).hasSize(6).doesNotHaveDuplicates().allSatisfy(nm -> assertThat(nm).isBetween(1, 20));
    }

    @Test
    void lineupsAndSettingsAreValidated() {
        Member a = member("gvA"), b = member("gvB"), c = member("gvC"), outsider = member("gvOut");
        assertThat(a.client().post("/api/games", game("X01", List.of(a.id(), b.id()), false, Map.of())).status())
                .as("two players is a normal match").isEqualTo(400);
        assertThat(a.client().post("/api/games", game("X01", List.of(a.id(), b.id(), b.id()), false, Map.of())).code()).isEqualTo("MATCH_PLAYERS_SAME");
        assertThat(a.client().post("/api/games", game("X01", List.of(a.id(), b.id(), c.id()), true, Map.of())).status()).isEqualTo(400);
        assertThat(a.client().post("/api/games", game("CRICKET", List.of(a.id(), b.id()), false, Map.of("roundLimit", 3))).status()).isEqualTo(400);
        assertThat(a.client().post("/api/games", game("SHANGHAI", List.of(a.id(), b.id()), false, Map.of())).status()).isEqualTo(400);
        assertThat(a.client().post("/api/games", game("CRICKET", List.of(a.id(), 99_999_999L), false, Map.of())).status()).isEqualTo(400);
        // you can't set up a game for other people
        assertThat(outsider.client().post("/api/games", game("CRICKET", List.of(a.id(), b.id()), false, Map.of())).status()).isEqualTo(403);
        assertThat(admin.post("/api/games", game("CRICKET", List.of(a.id(), b.id()), false, Map.of())).status()).isEqualTo(201);
    }

    @Test
    void onlyPlayersCreatorsAndAdminsScoreAndOneDartCannotLandTwice() throws Exception {
        Member a = member("gsA"), b = member("gsB"), outsider = member("gsO");
        String id = create(a.client(), game("CRICKET", List.of(a.id(), b.id()), false, Map.of())).get("id").asString();
        assertThat(dart(outsider.client(), id, 20, 3, 0).code()).isEqualTo("MATCH_NOT_SCORER");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ApiClient c = i % 2 == 0 ? a.client() : b.client();
            results.add(pool.submit(() -> dart(c, id, 20, 3, 0)));
        }
        int ok = 0;
        for (Future<Response> f : results) {
            Response r = f.get();
            if (r.status() == 200) ok++;
            else {
                assertThat(r.code()).isEqualTo("MATCH_OUT_OF_SYNC");
                assertThat(r.body().get("details").get("dartCount").asInt()).isEqualTo(1);
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        // invalid darts are refused
        assertThat(dart(a.client(), id, 25, 3, 1).status()).isEqualTo(400);
        assertThat(dart(a.client(), id, 21, 1, 1).status()).isEqualTo(400);
    }

    @Test
    void undoAndAbandon() {
        Member a = member("guA"), b = member("guB"), c = member("guC");
        String id = create(a.client(), game("X01", List.of(a.id(), b.id(), c.id()), false, Map.of("startScore", 301))).get("id").asString();
        assertThat(a.client().post("/api/games/" + id + "/undo", Map.of("expectedDartCount", 1)).code()).isEqualTo("MATCH_NOTHING_TO_UNDO");
        dart(a.client(), id, 20, 3, 0);
        dart(a.client(), id, 20, 3, 1);
        JsonNode undone = a.client().post("/api/games/" + id + "/undo", Map.of("expectedDartCount", 2)).body();
        assertThat(undone.get("dartCount").asInt()).isEqualTo(1);
        assertThat(undone.get("sides").get(0).get("remaining").asInt()).isEqualTo(241);
        assertThat(undone.get("lastDart").asString()).isEqualTo("T20");
        assertThat(a.client().post("/api/games/" + id + "/undo", Map.of("expectedDartCount", 2)).code()).isEqualTo("MATCH_OUT_OF_SYNC");
        Member outsider = member("guOut");
        assertThat(outsider.client().post("/api/games/" + id + "/abandon", null).code()).isEqualTo("MATCH_NOT_SCORER");
        assertThat(b.client().post("/api/games/" + id + "/abandon", null).body().get("status").asString()).isEqualTo("ABANDONED");
        assertThat(a.client().get("/api/games/live").body().toString()).doesNotContain(id);
        assertThat(a.client().get("/api/users/" + a.username() + "/game-stats").body().get("types").toString()).doesNotContain("\"played\":1");
    }

    @Test
    void liveAndRecentListsAndTheSocketFollowTheGame() throws Exception {
        Member a = member("glA"), b = member("glB");
        String id = create(a.client(), game("CRICKET", List.of(a.id(), b.id()), false, Map.of())).get("id").asString();
        assertThat(a.client().get("/api/games/live").body().toString()).contains(id);
        StompProbe p = StompProbe.connect(port, a.client().post("/api/ws/ticket", null).body().get("ticket").asString());
        p.subscribe("/topic/games/" + id);
        dart(b.client(), id, 19, 2, 0);
        JsonNode pushed = p.nextMessage();
        assertThat(pushed.get("id").asString()).isEqualTo(id);
        assertThat(pushed.get("sides").get(0).get("marks").get("19").asInt()).isEqualTo(2);
        p.close();
        assertThat(a.client().get("/api/games/recent?user=" + a.username()).status()).isEqualTo(200);
        assertThat(a.client().get("/api/games/recent?user=nobody_here").status()).isEqualTo(404);
        assertThat(a.client().get("/api/games/" + java.util.UUID.randomUUID()).status()).isEqualTo(404);
    }

    @Test
    void cricketScoreboardShowsMarksPointsAndRounds() {
        Member a = member("gcA"), b = member("gcB"), c = member("gcC");
        String id = create(a.client(), game("CRICKET", List.of(a.id(), b.id(), c.id()), false, Map.of("roundLimit", 0, "legsToWin", 2))).get("id").asString();
        dart(a.client(), id, 20, 3, 0);
        JsonNode s = dart(a.client(), id, 20, 3, 1).body();
        assertThat(s.get("sides").get(0).get("points").asInt()).isEqualTo(60);
        assertThat(s.get("settings").get("roundLimit").asInt()).isZero();
        assertThat(s.get("settings").get("legsToWin").asInt()).isEqualTo(2);
        assertThat(s.get("round").asInt()).isEqualTo(1);
        assertThat(s.get("turnDarts").size()).isEqualTo(2);
    }
}
