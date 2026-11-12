package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Kudos, comments, replies and likes: ownership, threading rules and hostile input. */
class SocialIT extends IntegrationTestBase {

    Member p1, p2, fan, other;
    String matchId;

    @BeforeAll
    void setUpMatch() {
        p1 = member("socP1");
        p2 = member("socP2");
        fan = member("socFan");
        other = member("socOth");
        matchId = finishedMatch(p1.client(), p1.id(), p2.id(), true, 0).get("id").asString();
    }

    private JsonNode comment(Member who, String body, Long parent) {
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("body", body);
        if (parent != null) req.put("parentId", parent);
        Response r = who.client().post("/api/matches/" + matchId + "/comments", req);
        assertThat(r.status()).as("%s", r.body()).isEqualTo(200);
        return r.body();
    }

    private static JsonNode find(JsonNode social, String body) {
        for (JsonNode c : social.get("comments")) {
            if (c.get("body").asString().equals(body)) return c;
            for (JsonNode r : c.get("replies")) if (r.get("body").asString().equals(body)) return r;
        }
        throw new AssertionError("comment not found: " + body);
    }

    @Test
    void kudosIsOnePerPersonAndNotForYourOwnMatch() {
        assertThat(p1.client().put("/api/matches/" + matchId + "/kudos", null).code()).isEqualTo("KUDOS_OWN_MATCH");
        assertThat(fan.client().put("/api/matches/" + matchId + "/kudos", null).status()).isEqualTo(200);
        JsonNode twice = fan.client().put("/api/matches/" + matchId + "/kudos", null).body();
        assertThat(twice.get("kudos").get("mine").asBoolean()).isTrue();
        int count = twice.get("kudos").get("count").asInt();
        assertThat(fan.client().delete("/api/matches/" + matchId + "/kudos").body().get("kudos").get("count").asInt()).isEqualTo(count - 1);
        assertThat(fan.client().delete("/api/matches/" + matchId + "/kudos").status()).as("removing twice is harmless").isEqualTo(200);
        assertThat(fan.client().put("/api/matches/" + UUID.randomUUID() + "/kudos", null).status()).isEqualTo(404);
    }

    @Test
    void repliesStayOneLevelDeepAndCannotCrossMatches() {
        String topText = "Top " + unique("t");
        long top = find(comment(fan, topText, null), topText).get("id").asLong();
        JsonNode r1 = comment(p1, "reply-1-" + top, top);
        long reply = find(r1, "reply-1-" + top).get("id").asLong();
        JsonNode r2 = comment(p2, "reply-2-" + top, reply);
        JsonNode nested = find(r2, "reply-2-" + top);
        assertThat(nested.get("replies").size()).as("a reply to a reply joins the same thread").isZero();
        boolean inThread = false;
        for (JsonNode c : r2.get("comments")) {
            if (c.get("id").asLong() == top) inThread = c.get("replies").size() == 2;
        }
        assertThat(inThread).isTrue();

        String otherMatch = finishedMatch(other.client(), other.id(), fan.id(), false, 0).get("id").asString();
        Response cross = fan.client().post("/api/matches/" + otherMatch + "/comments", Map.of("body", "sneaky", "parentId", top));
        assertThat(cross.status()).isEqualTo(404);
    }

    @Test
    void onlyTheAuthorEditsAndOnlyTheAuthorOrAnAdminDeletes() {
        String mineText = "Mine " + unique("m");
        JsonNode mine = find(comment(fan, mineText, null), mineText);
        long id = mine.get("id").asLong();
        assertThat(mine.get("canEdit").asBoolean()).isTrue();
        assertThat(other.client().patch("/api/comments/" + id, Map.of("body", "hijacked")).status()).isEqualTo(403);
        assertThat(other.client().delete("/api/comments/" + id).status()).isEqualTo(403);
        assertThat(admin.patch("/api/comments/" + id, Map.of("body", "admin edit")).status()).as("admins moderate, they don't put words in mouths").isEqualTo(403);
        JsonNode edited = fan.client().patch("/api/comments/" + id, Map.of("body", "edited text")).body();
        assertThat(find(edited, "edited text").get("editedAt").isNull()).isFalse();
        assertThat(admin.delete("/api/comments/" + id).status()).isEqualTo(200);
        assertThat(fan.client().patch("/api/comments/" + id, Map.of("body", "back from the dead")).status()).isIn(403, 404);
        assertThat(fan.client().delete("/api/comments/987654321").status()).isEqualTo(404);
    }

    @Test
    void deletingACommentWithRepliesKeepsTheThreadButHidesTheText() {
        String text = "Parent " + unique("p");
        JsonNode s = comment(fan, text, null);
        long parent = find(s, text).get("id").asLong();
        comment(p2, "child of " + parent, parent);
        JsonNode after = fan.client().delete("/api/comments/" + parent).body();
        JsonNode ghost = null;
        for (JsonNode c : after.get("comments")) if (c.get("id").asLong() == parent) ghost = c;
        assertThat(ghost).isNotNull();
        assertThat(ghost.get("deleted").asBoolean()).isTrue();
        assertThat(ghost.get("body").asString()).isEmpty();
        assertThat(ghost.get("replies").size()).isEqualTo(1);
        // a like on the deleted text is refused, and the deleted text is gone from the response entirely
        assertThat(other.client().put("/api/comments/" + parent + "/like", null).status()).isEqualTo(404);
        assertThat(after.toString()).doesNotContain(text);
    }

    @Test
    void likesAreIdempotent() {
        String text = "Like me " + unique("l");
        long id = find(comment(fan, text, null), text).get("id").asLong();
        other.client().put("/api/comments/" + id + "/like", null);
        JsonNode s = other.client().put("/api/comments/" + id + "/like", null).body();
        assertThat(find(s, text).get("likes").asInt()).isEqualTo(1);
        assertThat(find(s, text).get("likedByMe").asBoolean()).isTrue();
        JsonNode un = other.client().delete("/api/comments/" + id + "/like").body();
        assertThat(find(un, text).get("likes").asInt()).isZero();
    }

    @Test
    void hostileOrEmptyCommentBodiesAreHandled() {
        String html = "<img src=x onerror=alert(1)> " + unique("h");
        JsonNode s = comment(fan, html, null);
        assertThat(find(s, html).get("body").asString()).as("stored verbatim as text").isEqualTo(html);
        assertThat(fan.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "   \n ")).status()).isEqualTo(400);
        assertThat(fan.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "x".repeat(1001))).status()).isEqualTo(400);
        Response nul = fan.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "nul\u0000byte"));
        assertThat(nul.status()).isEqualTo(400);
        assertThat(nul.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(fan.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "rtl ‮gnirts")).status()).isEqualTo(400);
        assertThat(fan.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "x".repeat(1000))).status()).isEqualTo(200);
        assertThat(fan.client().post("/api/matches/" + UUID.randomUUID() + "/comments", Map.of("body", "ghost")).status()).isEqualTo(404);
    }

    @Test
    void matchListsCarryTheSocialCounts() {
        JsonNode recent = fan.client().get("/api/matches/recent?user=" + p1.username()).body();
        JsonNode row = null;
        for (JsonNode m : recent) if (m.get("id").asString().equals(matchId)) row = m;
        assertThat(row).isNotNull();
        JsonNode social = fan.client().get("/api/matches/" + matchId + "/social").body();
        assertThat(row.get("comments").asInt()).isEqualTo(social.get("commentCount").asInt());
        assertThat(row.get("kudos").asInt()).isEqualTo(social.get("kudos").get("count").asInt());
    }
}
