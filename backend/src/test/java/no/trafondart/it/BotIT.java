package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Practice matches against Trafo, the bot: the bot plays its own turns, undo and giving up behave, and – the point of
 * the feature – nothing about them reaches the rating, the leaderboard or the match stats. They have their own record.
 */
class BotIT extends IntegrationTestBase {

    static Map<String, Object> botBody(String level, int startScore, String outRule, int legs, Integer starter) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("startScore", startScore);
        m.put("inRule", "STRAIGHT");
        m.put("outRule", outRule);
        m.put("legsToWin", legs);
        m.put("startingPlayer", starter);
        return m;
    }

    private static String id(Response r) {
        return r.body().get("id").asString();
    }

    private Response dart(ApiClient c, String id, int segment, int multiplier, JsonNode state) {
        return c.post("/api/bot-matches/" + id + "/throws",
                Map.of("segment", segment, "multiplier", multiplier, "expectedDartCount", state.get("dartCount").asInt()));
    }

    /** 301 straight out, you first: T20 T20 T20, then T20 T20 S1. A beginner can't score 301 in the one turn it gets. */
    private JsonNode winQuickly(ApiClient c, String level) {
        Response created = c.post("/api/bot-matches", botBody(level, 301, "STRAIGHT", 1, 0));
        assertThat(created.status()).as("create: %s", created.body()).isEqualTo(201);
        JsonNode s = created.body();
        String id = id(created);
        int[][] darts = {{20, 3}, {20, 3}, {20, 3}, {20, 3}, {20, 3}, {1, 1}};
        for (int[] d : darts) {
            Response r = dart(c, id, d[0], d[1], s);
            assertThat(r.status()).as("dart: %s", r.body()).isEqualTo(200);
            s = r.body();
        }
        return s;
    }

    /** You miss every dart until Trafo has won. */
    private JsonNode loseToTrafo(ApiClient c, String level) {
        Response created = c.post("/api/bot-matches", botBody(level, 301, "STRAIGHT", 1, 0));
        assertThat(created.status()).isEqualTo(201);
        JsonNode s = created.body();
        String id = id(created);
        int guard = 0;
        while ("IN_PROGRESS".equals(s.get("status").asString())) {
            assertThat(guard++).as("Trafo finishes").isLessThan(300);
            s = dart(c, id, 0, 0, s).body();
        }
        return s;
    }

    @Test
    void trafoThrowsItsOwnTurnsRightAway() {
        Member a = member("botTurn");
        // Trafo starts: its first turn is already thrown when the match is created.
        Response created = a.client().post("/api/bot-matches", botBody("CLUB", 501, "DOUBLE", 1, 1));
        assertThat(created.status()).isEqualTo(201);
        JsonNode s = created.body();
        assertThat(s.get("level").asString()).isEqualTo("CLUB");
        assertThat(s.get("rated").asBoolean()).isFalse();
        assertThat(s.get("players").get(0).get("id").asLong()).isEqualTo(a.id());
        assertThat(s.get("players").get(1).get("realName").asString()).isEqualTo("Trafo");
        assertThat(s.get("dartCount").asInt()).isEqualTo(3);
        assertThat(s.get("currentPlayerIndex").asInt()).isZero();
        assertThat(s.get("botTurns")).hasSize(1);
        assertThat(s.get("botTurns").get(0).get("darts")).hasSize(3);
        assertThat(s.get("playerTurn").isNull()).isTrue();
        assertThat(s.get("remaining").get(1).asInt()).isEqualTo(501 - s.get("botTurns").get(0).get("total").asInt());

        // Your turn: after your third dart, Trafo answers in the same request.
        String id = id(created);
        s = dart(a.client(), id, 20, 1, s).body();
        assertThat(s.get("dartCount").asInt()).isEqualTo(4);
        assertThat(s.get("botTurns")).isEmpty();
        s = dart(a.client(), id, 20, 1, s).body();
        s = dart(a.client(), id, 20, 1, s).body();
        assertThat(s.get("dartCount").asInt()).isEqualTo(9);
        assertThat(s.get("currentPlayerIndex").asInt()).isZero();
        assertThat(s.get("remaining").get(0).asInt()).isEqualTo(441);
        assertThat(s.get("botTurns")).hasSize(1);
        // Your finished turn is still there, under Trafo's answer.
        assertThat(s.get("playerTurn").get("total").asInt()).isEqualTo(60);
        assertThat(s.get("playerTurn").get("darts")).hasSize(3);

        // A stale dart count is refused, as for matches.
        assertThat(a.client().post("/api/bot-matches/" + id + "/throws",
                Map.of("segment", 20, "multiplier", 1, "expectedDartCount", 4)).code()).isEqualTo("MATCH_OUT_OF_SYNC");
    }

    @Test
    void undoTakesBackYourLastDartAndTrafosAnswerToIt() {
        Member a = member("botUndo");
        Response created = a.client().post("/api/bot-matches", botBody("LEAGUE", 501, "DOUBLE", 1, 0));
        String id = id(created);
        JsonNode s = created.body();
        assertThat(a.client().post("/api/bot-matches/" + id + "/undo", Map.of("expectedDartCount", 1)).code()).isEqualTo("MATCH_NOTHING_TO_UNDO");
        s = dart(a.client(), id, 20, 3, s).body();
        s = dart(a.client(), id, 20, 3, s).body();
        s = dart(a.client(), id, 19, 3, s).body();
        assertThat(s.get("dartCount").asInt()).isEqualTo(6);
        JsonNode trafoFirst = s.get("botTurns").get(0);

        s = a.client().post("/api/bot-matches/" + id + "/undo", Map.of("expectedDartCount", 6)).body();
        assertThat(s.get("dartCount").asInt()).isEqualTo(2);
        assertThat(s.get("currentPlayerIndex").asInt()).isZero();
        assertThat(s.get("remaining").get(0).asInt()).isEqualTo(381);

        // The same dart again gets the same answer: undo can't re-roll Trafo's darts.
        s = dart(a.client(), id, 19, 3, s).body();
        assertThat(s.get("botTurns").get(0)).isEqualTo(trafoFirst);
    }

    @Test
    void winsAndLossesGoToTheBotRecordAndNowhereElse() {
        Member a = member("botRec"), b = member("botRecB");
        JsonNode before = a.client().get("/api/users/" + a.username() + "/stats").body();
        JsonNode boardBefore = a.client().get("/api/users/leaderboard").body();

        JsonNode won = winQuickly(a.client(), "BEGINNER");
        assertThat(won.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(won.get("winnerIndex").asInt()).isZero();
        winQuickly(a.client(), "CLUB");
        JsonNode lost = loseToTrafo(a.client(), "PRO");
        assertThat(lost.get("winnerIndex").asInt()).isEqualTo(1);
        winQuickly(a.client(), "CASUAL");

        JsonNode rec = a.client().get("/api/users/" + a.username() + "/bot-record").body();
        assertThat(rec.get("played").asInt()).isEqualTo(4);
        assertThat(rec.get("won").asInt()).isEqualTo(3);
        assertThat(rec.get("lost").asInt()).isEqualTo(1);
        assertThat(rec.get("currentStreak").asInt()).isEqualTo(1);
        assertThat(rec.get("bestStreak").asInt()).isEqualTo(2);
        assertThat(rec.get("hardestBeaten").asString()).isEqualTo("CLUB");
        assertThat(rec.get("levels")).hasSize(5);
        JsonNode pro = rec.get("levels").get(4);
        assertThat(pro.get("level").asString()).isEqualTo("PRO");
        assertThat(pro.get("played").asInt()).isEqualTo(1);
        assertThat(pro.get("won").asInt()).isZero();
        assertThat(rec.get("throwing").get("count180").asInt()).isEqualTo(3);
        assertThat(rec.get("throwing").get("highestCheckout").asInt()).isEqualTo(121);
        assertThat(rec.get("throwing").get("average").asDouble()).isPositive();
        assertThat(rec.get("recent")).hasSize(4);
        assertThat(rec.get("recent").get(0).get("level").asString()).isEqualTo("CASUAL");

        // Rating, profile stats and leaderboard: untouched.
        JsonNode after = a.client().get("/api/users/" + a.username() + "/stats").body();
        assertThat(after.get("matchesPlayed").asInt()).isEqualTo(before.get("matchesPlayed").asInt());
        assertThat(after.get("count180").asInt()).isEqualTo(before.get("count180").asInt());
        assertThat(after.get("player").get("rating").asInt()).isEqualTo(before.get("player").get("rating").asInt());
        assertThat(after.get("recentForm")).isEqualTo(before.get("recentForm"));
        assertThat(after.get("currentStreak")).isEqualTo(before.get("currentStreak"));
        assertThat(a.client().get("/api/users/leaderboard").body()).isEqualTo(boardBefore);
        assertThat(a.client().get("/api/matches/recent?user=" + a.username()).body()).isEmpty();
        assertThat(a.client().get("/api/matches/live").body().toString()).doesNotContain(a.username());

        // Anyone can see the record; only you can see your matches.
        assertThat(b.client().get("/api/users/" + a.username() + "/bot-record").body().get("played").asInt()).isEqualTo(4);
        assertThat(b.client().get("/api/bot-matches/" + won.get("id").asString()).status()).isEqualTo(404);
        assertThat(rec.has("openMatchId")).isTrue();
    }

    @Test
    void givingUpCountsAsALossOnceYouHaveThrown() {
        Member a = member("botQuit");
        winQuickly(a.client(), "BEGINNER");

        // Nothing thrown yet: giving up just removes the match.
        Response fresh = a.client().post("/api/bot-matches", botBody("PRO", 501, "DOUBLE", 3, 0));
        assertThat(a.client().post("/api/bot-matches/" + id(fresh) + "/forfeit", null).status()).isEqualTo(204);
        assertThat(a.client().get("/api/bot-matches/" + id(fresh)).status()).isEqualTo(404);

        // One at a time.
        Response started = a.client().post("/api/bot-matches", botBody("PRO", 501, "DOUBLE", 3, 0));
        Response second = a.client().post("/api/bot-matches", botBody("CLUB", 501, "DOUBLE", 1, 0));
        assertThat(second.code()).isEqualTo("BOT_MATCH_IN_PROGRESS");
        assertThat(second.body().get("details").get("id").asString()).isEqualTo(id(started));
        assertThat(a.client().get("/api/users/" + a.username() + "/bot-record").body().get("openMatchId").asString()).isEqualTo(id(started));

        dart(a.client(), id(started), 20, 1, started.body());
        Response quit = a.client().post("/api/bot-matches/" + id(started) + "/forfeit", null);
        assertThat(quit.status()).isEqualTo(200);
        assertThat(quit.body().get("status").asString()).isEqualTo("FORFEITED");
        assertThat(quit.body().get("winnerIndex").asInt()).isEqualTo(1);
        assertThat(dart(a.client(), id(started), 20, 1, quit.body()).code()).isEqualTo("MATCH_NOT_IN_PROGRESS");

        JsonNode rec = a.client().get("/api/users/" + a.username() + "/bot-record").body();
        assertThat(rec.get("played").asInt()).isEqualTo(2);
        assertThat(rec.get("lost").asInt()).isEqualTo(1);
        assertThat(rec.get("currentStreak").asInt()).isZero();
        assertThat(rec.get("openMatchId").isNull()).isTrue();
    }

    @Test
    void settingsAreValidatedAndOthersCantPlayYourMatch() {
        Member a = member("botCfg"), b = member("botCfgB");
        assertThat(a.client().post("/api/bot-matches", botBody("GOD", 501, "DOUBLE", 1, 0)).status()).isEqualTo(400);
        assertThat(a.client().post("/api/bot-matches", botBody("CLUB", 500, "DOUBLE", 1, 0)).status()).isEqualTo(400);
        assertThat(a.client().post("/api/bot-matches", botBody("CLUB", 501, "DOUBLE", 12, 0)).status()).isEqualTo(400);
        assertThat(a.client().post("/api/bot-matches", botBody("CLUB", 501, "DOUBLE", 1, 2)).status()).isEqualTo(400);
        Response r = a.client().post("/api/bot-matches", botBody("CLUB", 401, "DOUBLE", 2, null));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("config").get("startScore").asInt()).isEqualTo(401);
        String id = id(r);
        assertThat(dart(b.client(), id, 20, 1, r.body()).status()).isEqualTo(404);
        assertThat(b.client().post("/api/bot-matches/" + id + "/forfeit", null).status()).isEqualTo(404);
        assertThat(client().get("/api/bot-matches/" + id).status()).isEqualTo(401);
    }

    @Test
    void yourBotMatchesAreInYourExportAndGoWithYourAccount() {
        Member a = member("botGone");
        winQuickly(a.client(), "BEGINNER");
        JsonNode export = a.client().get("/api/users/me/export").body();
        assertThat(export.get("botMatches")).hasSize(1);
        assertThat(export.get("botMatches").get(0).get("won").asBoolean()).isTrue();
        assertThat(export.get("botMatches").get(0).get("myDarts")).hasSize(6);

        assertThat(a.client().delete("/api/users/me", Map.of("password", a.password())).status()).isEqualTo(204);
        assertThat(admin.get("/api/users/" + a.username() + "/bot-record").status()).isEqualTo(404);
    }
}
