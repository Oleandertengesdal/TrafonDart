package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Leagues and cups from creation to champion, plus the ways a bracket could be broken. */
class TournamentIT extends IntegrationTestBase {

    private Map<String, Object> body(String format, List<Long> ids, boolean rated) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", unique("T"));
        b.put("format", format);
        b.put("startScore", 301);
        b.put("inRule", "STRAIGHT");
        b.put("outRule", "STRAIGHT");
        b.put("legsToWin", 1);
        b.put("rated", rated);
        b.put("participantIds", ids);
        return b;
    }

    private String create(ApiClient c, String format, List<Long> ids) {
        Response r = c.post("/api/tournaments", body(format, ids, false));
        assertThat(r.status()).as("%s", r.body()).isEqualTo(201);
        return r.body().get("summary").get("id").asString();
    }

    /** Plays every READY fixture (lowest player id wins) until nothing is ready. */
    private JsonNode playAll(String tid) {
        JsonNode t = admin.get("/api/tournaments/" + tid).body();
        int guard = 0;
        while (true) {
            assertThat(guard++).isLessThan(100);
            JsonNode ready = null;
            for (JsonNode f : t.get("fixtures")) if (f.get("status").asString().equals("READY")) ready = f;
            if (ready == null) return t;
            Response started = admin.post("/api/tournaments/" + tid + "/fixtures/" + ready.get("id").asLong() + "/start", null);
            assertThat(started.status()).as("%s", started.body()).isEqualTo(200);
            String mid = started.body().get("matchId").asString();
            int winner = ready.get("player1").get("id").asLong() < ready.get("player2").get("id").asLong() ? 0 : 1;
            playToEnd(admin, mid, winner);
            t = admin.get("/api/tournaments/" + tid).body();
        }
    }

    @Test
    void roundRobinOfFourPlaysSixMatchesAndRanksByPoints() {
        List<Member> ms = List.of(member("rrA"), member("rrB"), member("rrC"), member("rrD"));
        List<Long> ids = ms.stream().map(Member::id).toList();
        String tid = create(ms.get(0).client(), "ROUND_ROBIN", ids);
        JsonNode started = ms.get(0).client().post("/api/tournaments/" + tid + "/start", null).body();
        assertThat(started.get("fixtures").size()).isEqualTo(6);
        JsonNode done = playAll(tid);
        assertThat(done.get("summary").get("status").asString()).isEqualTo("COMPLETED");
        JsonNode standings = done.get("standings");
        assertThat(standings.size()).isEqualTo(4);
        int totalWins = 0, totalPlayed = 0;
        for (JsonNode row : standings) {
            totalWins += row.get("won").asInt();
            totalPlayed += row.get("played").asInt();
            assertThat(row.get("played").asInt()).isEqualTo(3);
        }
        assertThat(totalWins).isEqualTo(6);
        assertThat(totalPlayed).isEqualTo(12);
        // lowest id beat everyone
        assertThat(standings.get(0).get("player").get("id").asLong()).isEqualTo(ids.stream().min(Long::compare).orElseThrow());
        assertThat(standings.get(0).get("won").asInt()).isEqualTo(3);
        for (int i = 1; i < standings.size(); i++) {
            assertThat(standings.get(i - 1).get("points").asInt()).isGreaterThanOrEqualTo(standings.get(i).get("points").asInt());
        }
    }

    @Test
    void knockoutOfFiveGivesByesAndCrownsAChampion() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(member("ko" + i).id());
        String tid = create(admin, "SINGLE_ELIMINATION", ids);
        JsonNode started = admin.post("/api/tournaments/" + tid + "/start", null).body();
        long byes = 0;
        for (JsonNode f : started.get("fixtures")) if (f.get("status").asString().equals("BYE")) byes++;
        assertThat(byes).isEqualTo(3);
        JsonNode done = playAll(tid);
        assertThat(done.get("summary").get("status").asString()).isEqualTo("COMPLETED");
        JsonNode fin = null;
        for (JsonNode f : done.get("fixtures")) if (fin == null || f.get("round").asInt() > fin.get("round").asInt()) fin = f;
        assertThat(fin.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("summary").get("winner").isNull()).isFalse();
    }

    @Test
    void participantRulesAreEnforced() {
        Member a = member("prA"), b = member("prB");
        // duplicates collapse to one entry
        String tid = create(a.client(), "ROUND_ROBIN", List.of(a.id(), b.id(), b.id()));
        assertThat(a.client().get("/api/tournaments/" + tid).body().get("participants").size()).isEqualTo(2);
        // unknown ids are refused
        assertThat(a.client().put("/api/tournaments/" + tid + "/participants", Map.of("participantIds", List.of(a.id(), 99_999_999L))).status()).isEqualTo(400);
        // a single player can't start a tournament
        String lonely = create(a.client(), "ROUND_ROBIN", List.of(a.id()));
        assertThat(a.client().post("/api/tournaments/" + lonely + "/start", null).code()).isEqualTo("TOURNAMENT_TOO_FEW_PLAYERS");
        // once started, the field is frozen and it can't start twice
        a.client().post("/api/tournaments/" + tid + "/start", null);
        assertThat(a.client().put("/api/tournaments/" + tid + "/participants", Map.of("participantIds", List.of(a.id()))).code()).isEqualTo("TOURNAMENT_NOT_DRAFT");
        assertThat(a.client().post("/api/tournaments/" + tid + "/start", null).code()).isEqualTo("TOURNAMENT_NOT_DRAFT");
        // cancelling twice is a conflict, and a cancelled tournament can't start fixtures
        assertThat(a.client().post("/api/tournaments/" + tid + "/cancel", null).status()).isEqualTo(200);
        assertThat(a.client().post("/api/tournaments/" + tid + "/cancel", null).code()).isEqualTo("CONFLICT");
        long fixture = a.client().get("/api/tournaments/" + tid).body().get("fixtures").get(0).get("id").asLong();
        assertThat(a.client().post("/api/tournaments/" + tid + "/fixtures/" + fixture + "/start", null).code()).isEqualTo("TOURNAMENT_NOT_IN_PROGRESS");
    }

    @Test
    void namesAndSettingsAreValidated() {
        Member a = member("tvA"), b = member("tvB");
        List<Long> ids = List.of(a.id(), b.id());
        Map<String, Object> blank = body("ROUND_ROBIN", ids, false);
        blank.put("name", "   ");
        assertThat(a.client().post("/api/tournaments", blank).status()).isEqualTo(400);
        Map<String, Object> nul = body("ROUND_ROBIN", ids, false);
        nul.put("name", "Cup\u0000");
        assertThat(a.client().post("/api/tournaments", nul).status()).isEqualTo(400);
        Map<String, Object> newline = body("ROUND_ROBIN", ids, false);
        newline.put("name", "Two\nlines");
        assertThat(a.client().post("/api/tournaments", newline).status()).isEqualTo(400);
        Map<String, Object> format = body("SWISS", ids, false);
        assertThat(a.client().post("/api/tournaments", format).status()).isEqualTo(400);
        Map<String, Object> many = body("ROUND_ROBIN", java.util.stream.LongStream.range(1, 200).boxed().toList(), false);
        assertThat(a.client().post("/api/tournaments", many).status()).isEqualTo(400);
    }

    @Test
    void startingAFixtureTwiceGivesTheSameMatchAndAbandoningReopensIt() {
        Member a = member("fxA"), b = member("fxB");
        String tid = create(a.client(), "ROUND_ROBIN", List.of(a.id(), b.id()));
        a.client().post("/api/tournaments/" + tid + "/start", null);
        long fixture = a.client().get("/api/tournaments/" + tid).body().get("fixtures").get(0).get("id").asLong();
        String m1 = a.client().post("/api/tournaments/" + tid + "/fixtures/" + fixture + "/start", null).body().get("matchId").asString();
        String m2 = b.client().post("/api/tournaments/" + tid + "/fixtures/" + fixture + "/start", null).body().get("matchId").asString();
        assertThat(m2).isEqualTo(m1);
        a.client().post("/api/matches/" + m1 + "/abandon", null);
        JsonNode f = a.client().get("/api/tournaments/" + tid).body().get("fixtures").get(0);
        assertThat(f.get("status").asString()).isEqualTo("READY");
        String m3 = a.client().post("/api/tournaments/" + tid + "/fixtures/" + fixture + "/start", null).body().get("matchId").asString();
        assertThat(m3).isNotEqualTo(m1);
    }
}
