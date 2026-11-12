package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Lifetime achievements are derived from what really happened, can't be farmed by others, and follow voids. */
class AchievementsIT extends IntegrationTestBase {

    private JsonNode achievements(ApiClient c, String username) {
        Response r = c.get("/api/users/" + username + "/achievements");
        assertThat(r.status()).isEqualTo(200);
        return r.body();
    }

    private static JsonNode item(JsonNode list, String code) {
        for (JsonNode i : list.get("items")) if (i.get("code").asString().equals(code)) return i;
        throw new AssertionError("no achievement " + code);
    }

    private static boolean unlocked(JsonNode list, String code) {
        return item(list, code).get("unlocked").asBoolean();
    }

    /** Throws a fixed list of darts; the other player misses whatever is needed in between. */
    private String scripted(ApiClient c, long p1, long p2, int start, String out, int[][] darts) {
        Response created = c.post("/api/matches", matchBody(p1, p2, true, start, out, 1));
        String id = created.body().get("id").asString();
        int n = 0;
        for (int[] d : darts) {
            Response r = throwDart(c, id, d[0], d[1], n++);
            assertThat(r.status()).as("%s", r.body()).isEqualTo(200);
        }
        return id;
    }

    @Test
    void aNineDarterUnlocksEverythingItShould() {
        Member a = member("achA"), b = member("achB");
        int[][] nine = {{20, 3}, {20, 3}, {20, 3}, {0, 0}, {0, 0}, {0, 0}, {20, 3}, {20, 3}, {20, 3}, {0, 0}, {0, 0}, {0, 0},
                {20, 3}, {19, 3}, {12, 2}};
        String id = scripted(a.client(), a.id(), b.id(), 501, "DOUBLE", nine);
        assertThat(a.client().get("/api/matches/" + id).body().get("status").asString()).isEqualTo("COMPLETED");

        JsonNode list = achievements(a.client(), a.username());
        for (String code : List.of("FIRST_WIN", "FIRST_180", "TON_PLUS_CHECKOUT", "SHORT_LEG", "NINE_DARTER", "AVERAGE_70")) {
            assertThat(unlocked(list, code)).as(code).isTrue();
            assertThat(item(list, code).get("matchId").asString()).isEqualTo(id);
            assertThat(item(list, code).get("unlockedAt").asString()).isNotBlank();
        }
        assertThat(unlocked(list, "BIG_FISH")).isFalse();
        assertThat(unlocked(list, "BULL_FINISH")).isFalse();
        assertThat(list.get("unlocked").asInt()).isEqualTo(list.get("items").size() - countLocked(list));
        JsonNode matches10 = item(list, "MATCHES_10");
        assertThat(matches10.get("progress").asInt()).isEqualTo(1);
        assertThat(matches10.get("target").asInt()).isEqualTo(10);

        // the loser got nothing but the count of matches played
        JsonNode loser = achievements(a.client(), b.username());
        assertThat(unlocked(loser, "FIRST_WIN")).isFalse();
        assertThat(item(loser, "MATCHES_10").get("progress").asInt()).isEqualTo(1);
    }

    private static int countLocked(JsonNode list) {
        int n = 0;
        for (JsonNode i : list.get("items")) if (!i.get("unlocked").asBoolean()) n++;
        return n;
    }

    @Test
    void aBullFinishAndABigFishAreRecognised() {
        Member a = member("bullA"), b = member("bullB");
        // 501: 180 (321 left), 151 (170 left), then the big fish: T20 T20 BULL
        int[][] darts = {{20, 3}, {20, 3}, {20, 3}, {0, 0}, {0, 0}, {0, 0}, {20, 3}, {19, 3}, {17, 2}, {0, 0}, {0, 0}, {0, 0},
                {20, 3}, {20, 3}, {25, 2}};
        scripted(a.client(), a.id(), b.id(), 501, "DOUBLE", darts);
        JsonNode list = achievements(a.client(), a.username());
        assertThat(unlocked(list, "BULL_FINISH")).isTrue();
        assertThat(unlocked(list, "BIG_FISH")).isTrue();
    }

    @Test
    void casualMatchesSetUpBySomeoneElseDoNotCount() {
        Member a = member("fakeA"), b = member("fakeB"), outsider = member("fakeO");
        String id = quickMatch(outsider.client(), a.id(), b.id(), false);
        for (int i = 0; i < 3; i++) throwDart(outsider.client(), id, 20, 3, i);
        playToEnd(outsider.client(), id, 0);
        JsonNode list = achievements(a.client(), a.username());
        assertThat(unlocked(list, "FIRST_180")).isFalse();
        assertThat(unlocked(list, "FIRST_WIN")).isFalse();
        // but a casual match a player set up themselves does count
        String own = quickMatch(a.client(), a.id(), b.id(), false);
        playToEnd(a.client(), own, 0);
        assertThat(unlocked(achievements(a.client(), a.username()), "FIRST_WIN")).isTrue();
    }

    @Test
    void voidingTheMatchTakesTheAchievementAway() {
        Member a = member("vachA"), b = member("vachB");
        String id = finishedMatch(a.client(), a.id(), b.id(), true, 0).get("id").asString();
        assertThat(unlocked(achievements(a.client(), a.username()), "FIRST_WIN")).isTrue();
        long did = b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "practice")).body().get("id").asLong();
        admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "VOID_MATCH"));
        assertThat(unlocked(achievements(a.client(), a.username()), "FIRST_WIN")).isFalse();
    }

    @Test
    void partyGamesUnlockTheirOwnAchievements() {
        Member a = member("pgA"), b = member("pgB");
        Map<String, Object> body = new HashMap<>();
        body.put("type", "AROUND_THE_CLOCK");
        body.put("playerIds", List.of(a.id()));
        body.put("teams", false);
        body.put("settings", Map.of("multiplierSkips", false));
        String id = a.client().post("/api/games", body).body().get("id").asString();
        for (int i = 0; i < 20; i++) a.client().post("/api/games/" + id + "/throws", Map.of("segment", i + 1, "multiplier", 1, "expectedDartCount", i));
        a.client().post("/api/games/" + id + "/throws", Map.of("segment", 25, "multiplier", 1, "expectedDartCount", 20));
        JsonNode list = achievements(a.client(), a.username());
        assertThat(unlocked(list, "CLOCK_UNDER_40")).isTrue();
        assertThat(item(list, "CLOCK_UNDER_40").get("gameId").asString()).isEqualTo(id);
        assertThat(unlocked(achievements(b.client(), b.username()), "CLOCK_UNDER_40")).isFalse();
    }

    @Test
    void unknownUsersAre404AndEveryAchievementHasACode() {
        Member m = member("achList");
        assertThat(m.client().get("/api/users/nobody_here/achievements").status()).isEqualTo(404);
        JsonNode list = achievements(m.client(), m.username());
        assertThat(list.get("items").size()).isGreaterThanOrEqualTo(20);
        assertThat(list.get("unlocked").asInt()).isZero();
        for (JsonNode i : list.get("items")) assertThat(i.get("code").asString()).matches("[A-Z0-9_]+");
    }
}
