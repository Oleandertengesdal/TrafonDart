package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Challenge a friend: invite, accept or decline, then start the match when you're both at the board. */
class ChallengesIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    static Map<String, Object> challenge(long opponentId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("opponentId", opponentId);
        m.put("startScore", 301);
        m.put("inRule", "STRAIGHT");
        m.put("outRule", "STRAIGHT");
        m.put("legsToWin", 1);
        m.put("rated", true);
        m.put("message", "Rematch tonight?");
        return m;
    }

    private static JsonNode find(JsonNode list, long id) {
        for (JsonNode c : list) if (c.get("id").asLong() == id) return c;
        return null;
    }

    @Test
    void aChallengeGoesFromInviteToAMatch() {
        Member a = member("chA"), b = member("chB"), c = member("chC");
        Response created = a.client().post("/api/challenges", challenge(b.id()));
        assertThat(created.status()).as("%s", created.body()).isEqualTo(201);
        JsonNode ch = created.body();
        long id = ch.get("id").asLong();
        assertThat(ch.get("status").asString()).isEqualTo("PENDING");
        assertThat(ch.get("challenger").get("username").asString()).isEqualTo(a.username());
        assertThat(ch.get("opponent").get("username").asString()).isEqualTo(b.username());
        assertThat(ch.get("message").asString()).isEqualTo("Rematch tonight?");
        Duration left = Duration.between(Instant.now(), Instant.parse(ch.get("expiresAt").asString()));
        assertThat(left).isBetween(Duration.ofHours(47), Duration.ofHours(48).plusMinutes(1));

        assertThat(find(b.client().get("/api/challenges").body().get("incoming"), id)).isNotNull();
        assertThat(find(a.client().get("/api/challenges").body().get("outgoing"), id)).isNotNull();
        // outsiders don't see it and can't touch it
        JsonNode theirs = c.client().get("/api/challenges").body();
        assertThat(find(theirs.get("incoming"), id)).isNull();
        assertThat(find(theirs.get("outgoing"), id)).isNull();
        assertThat(c.client().post("/api/challenges/" + id + "/accept", null).status()).isEqualTo(404);
        assertThat(c.client().post("/api/challenges/" + id + "/start", null).status()).isEqualTo(404);
        // you can't accept your own challenge, and nothing can start before it's accepted
        assertThat(a.client().post("/api/challenges/" + id + "/accept", null).status()).isEqualTo(403);
        assertThat(a.client().post("/api/challenges/" + id + "/start", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");

        Response accepted = b.client().post("/api/challenges/" + id + "/accept", null);
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.body().get("status").asString()).isEqualTo("ACCEPTED");
        assertThat(b.client().post("/api/challenges/" + id + "/decline", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");

        Response started = b.client().post("/api/challenges/" + id + "/start", null);
        assertThat(started.status()).as("%s", started.body()).isEqualTo(200);
        assertThat(started.body().get("status").asString()).isEqualTo("STARTED");
        String matchId = started.body().get("matchId").asString();
        JsonNode match = a.client().get("/api/matches/" + matchId).body();
        assertThat(match.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(match.get("rated").asBoolean()).isTrue();
        assertThat(match.get("config").get("startScore").asInt()).isEqualTo(301);
        assertThat(match.toString()).contains(a.username()).contains(b.username());
        // starting twice makes no second match
        assertThat(a.client().post("/api/challenges/" + id + "/start", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");
        // and a started challenge is no longer open
        assertThat(find(b.client().get("/api/challenges").body().get("incoming"), id)).isNull();
    }

    @Test
    void declineAndCancel() {
        Member a = member("chD1"), b = member("chD2");
        long first = a.client().post("/api/challenges", challenge(b.id())).body().get("id").asLong();
        Response declined = b.client().post("/api/challenges/" + first + "/decline", null);
        assertThat(declined.body().get("status").asString()).isEqualTo("DECLINED");
        assertThat(b.client().post("/api/challenges/" + first + "/accept", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");

        long second = a.client().post("/api/challenges", challenge(b.id())).body().get("id").asLong();
        // only the challenger cancels a pending challenge; the opponent declines instead
        assertThat(b.client().post("/api/challenges/" + second + "/cancel", null).status()).isEqualTo(403);
        assertThat(a.client().post("/api/challenges/" + second + "/cancel", null).body().get("status").asString()).isEqualTo("CANCELLED");

        // either player can call off an accepted challenge
        long third = a.client().post("/api/challenges", challenge(b.id())).body().get("id").asLong();
        b.client().post("/api/challenges/" + third + "/accept", null);
        assertThat(b.client().post("/api/challenges/" + third + "/cancel", null).body().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(a.client().post("/api/challenges/" + third + "/start", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");
    }

    @Test
    void badChallengesAreRefused() {
        Member a = member("chV1"), b = member("chV2");
        assertThat(a.client().post("/api/challenges", challenge(a.id())).code()).isEqualTo("MATCH_PLAYERS_SAME");
        assertThat(a.client().post("/api/challenges", challenge(999_999_999L)).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> odd = challenge(b.id());
        odd.put("startScore", 302);
        assertThat(a.client().post("/api/challenges", odd).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> legs = challenge(b.id());
        legs.put("legsToWin", 12);
        assertThat(a.client().post("/api/challenges", legs).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> longMsg = challenge(b.id());
        longMsg.put("message", "x".repeat(201));
        assertThat(a.client().post("/api/challenges", longMsg).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> missing = challenge(b.id());
        missing.remove("opponentId");
        assertThat(a.client().post("/api/challenges", missing).code()).isEqualTo("VALIDATION_FAILED");

        // control characters and bidi overrides are refused, like everywhere else free text is accepted
        Map<String, Object> messy = challenge(b.id());
        messy.put("message", "Hei\u0000der");
        assertThat(a.client().post("/api/challenges", messy).code()).isEqualTo("VALIDATION_FAILED");
        messy.put("message", "Hei ‮der");
        assertThat(a.client().post("/api/challenges", messy).code()).isEqualTo("VALIDATION_FAILED");
        // surrounding space is trimmed; a blank message is no message
        messy.put("message", "  Hei der  ");
        JsonNode ok = a.client().post("/api/challenges", messy).body();
        assertThat(ok.get("message").asString()).isEqualTo("Hei der");
        Member d = member("chV3");
        Map<String, Object> blank = challenge(d.id());
        blank.put("message", "   ");
        assertThat(a.client().post("/api/challenges", blank).body().get("message").isNull()).isTrue();

        // one open challenge per pair, whichever way round
        assertThat(a.client().post("/api/challenges", challenge(b.id())).code()).isEqualTo("CHALLENGE_EXISTS");
        assertThat(b.client().post("/api/challenges", challenge(a.id())).code()).isEqualTo("CHALLENGE_EXISTS");

        // challenging a disabled member is refused
        Member gone = member("chGone");
        admin.patch("/api/admin/users/" + gone.id(), Map.of("enabled", false));
        assertThat(a.client().post("/api/challenges", challenge(gone.id())).code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void nobodyCanSpamChallenges() {
        Member a = member("chSpam");
        for (int i = 0; i < 5; i++) {
            Member o = member("chT" + i);
            assertThat(a.client().post("/api/challenges", challenge(o.id())).status()).isEqualTo(201);
        }
        Member sixth = member("chT6");
        assertThat(a.client().post("/api/challenges", challenge(sixth.id())).code()).isEqualTo("CHALLENGE_LIMIT");
    }

    @Test
    void oldChallengesExpire() {
        Member a = member("chE1"), b = member("chE2");
        long id = a.client().post("/api/challenges", challenge(b.id())).body().get("id").asLong();
        jdbc.update("update challenges set expires_at = now() - interval '1 minute' where id = ?", id);
        assertThat(find(b.client().get("/api/challenges").body().get("incoming"), id)).isNull();
        assertThat(b.client().post("/api/challenges/" + id + "/accept", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");
        assertThat(jdbc.queryForObject("select status from challenges where id = ?", String.class, id)).isEqualTo("EXPIRED");
        // an expired challenge doesn't block a new one
        assertThat(a.client().post("/api/challenges", challenge(b.id())).status()).isEqualTo(201);

        // an accepted challenge that nobody starts runs out too
        Member c = member("chE3");
        long acc = a.client().post("/api/challenges", challenge(c.id())).body().get("id").asLong();
        c.client().post("/api/challenges/" + acc + "/accept", null);
        jdbc.update("update challenges set expires_at = now() - interval '1 minute' where id = ?", acc);
        assertThat(a.client().post("/api/challenges/" + acc + "/start", null).code()).isEqualTo("CHALLENGE_NOT_OPEN");
    }
}
