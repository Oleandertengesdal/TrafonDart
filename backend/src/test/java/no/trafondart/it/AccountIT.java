package no.trafondart.it;

import no.trafondart.it.ApiClient.Raw;
import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Your own data: download all of it, or delete your account for good. */
class AccountIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void theExportHasEverythingAboutMeAndNothingElse() {
        Member a = member("expA"), b = member("expB");
        String matchId = finishedMatch(a.client(), a.id(), b.id(), true, 0).get("id").asString();
        a.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "Godt spilt, takk for kampen"));
        b.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "Bs hemmelige kommentar"));

        Raw raw = a.client().raw("GET", "/api/users/me/export", null, null);
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.header("Content-Disposition")).startsWith("attachment").contains("trafondart-" + a.username());
        assertThat(raw.header("Cache-Control")).contains("no-store");
        assertThat(raw.header("Content-Type")).startsWith("application/json");
        JsonNode export = JSON.readTree(raw.body());
        assertThat(export.get("profile").get("username").asString()).isEqualTo(a.username());
        assertThat(export.get("exportedAt").asString()).isNotBlank();
        JsonNode match = export.get("matches").get(0);
        assertThat(match.get("id").asString()).isEqualTo(matchId);
        assertThat(match.get("opponent").asString()).isEqualTo(b.username());
        assertThat(match.get("won").asBoolean()).isTrue();
        assertThat(match.get("myDarts").size()).isPositive();
        assertThat(export.get("comments").toString()).contains("Godt spilt").doesNotContain("hemmelige");
        assertThat(export.get("ratingHistory").size()).isEqualTo(1);
        assertThat(export.has("achievements")).isTrue();
        assertThat(export.has("games")).isTrue();
        // nothing secret about the account itself
        assertThat(raw.body()).doesNotContain("$2a$", "$2b$", "passwordHash", "tokenVersion", PASSWORD);
        // only for yourself
        assertThat(client().get("/api/users/me/export").status()).isEqualTo(401);
    }

    @Test
    void exportsAreRateLimited() {
        Member m = member("expRate");
        int last = 0;
        for (int i = 0; i < 6; i++) last = m.client().get("/api/users/me/export").status();
        assertThat(last).isEqualTo(429);
    }

    @Test
    void deletingAnAccountAnonymisesItButKeepsTheMatches() {
        Member a = member("delA"), b = member("delB"), c = member("delC");
        String matchId = finishedMatch(a.client(), a.id(), b.id(), true, 0).get("id").asString();
        a.client().post("/api/matches/" + matchId + "/comments", Map.of("body", "Min kommentar"));
        String other = finishedMatch(b.client(), b.id(), c.id(), true, 0).get("id").asString();
        assertThat(a.client().put("/api/matches/" + other + "/kudos", null).status()).isEqualTo(200);
        assertThat(admin.put("/api/matches/" + other + "/kudos", null).status()).isEqualTo(200);
        long pending = b.client().post("/api/challenges", ChallengesIT.challenge(a.id())).body().get("id").asLong();

        // the password is required, and a wrong one changes nothing
        assertThat(a.client().delete("/api/users/me", Map.of("password", "wrong-password-1")).code()).isEqualTo("PASSWORD_INCORRECT");
        assertThat(a.client().delete("/api/users/me", Map.of()).code()).isEqualTo("VALIDATION_FAILED");
        assertThat(a.client().get("/api/auth/me").status()).isEqualTo(200);

        Response deleted = a.client().delete("/api/users/me", Map.of("password", a.password()));
        assertThat(deleted.status()).isEqualTo(204);
        // logged out here, and the old session cookie is dead everywhere
        assertThat(a.client().get("/api/auth/me").status()).isEqualTo(401);
        assertThat(client().login(a.username(), a.password()).code()).isEqualTo("AUTH_INVALID_CREDENTIALS");

        // the match is still there, played by "a deleted player"
        JsonNode match = b.client().get("/api/matches/" + matchId).body();
        assertThat(match.get("status").asString()).isEqualTo("COMPLETED");
        JsonNode ghost = match.get("players").get(0);
        assertThat(ghost.get("deleted").asBoolean()).isTrue();
        assertThat(ghost.get("username").asString()).isNotEqualTo(a.username());
        assertThat(ghost.get("realName").asString()).isEqualTo("Slettet spiller");
        assertThat(match.get("players").get(1).get("deleted").asBoolean()).isFalse();
        assertThat(match.toString()).doesNotContain(a.username()).doesNotContain("Test " + a.username().replaceAll("[^A-Za-z]", ""));

        // their comment text is gone, their kudos too
        JsonNode social = b.client().get("/api/matches/" + matchId + "/social").body();
        assertThat(social.toString()).doesNotContain("Min kommentar");
        assertThat(b.client().get("/api/matches/" + other + "/social").body().get("kudos").get("count").asInt()).isEqualTo(1);

        // they're out of every list, and the challenge to them is called off
        assertThat(b.client().get("/api/users").body().toString()).doesNotContain(ghost.get("username").asString());
        assertThat(b.client().get("/api/users/leaderboard").body().toString()).doesNotContain(ghost.get("username").asString());
        assertThat(b.client().get("/api/users/" + a.username() + "/stats").status()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select status from challenges where id = ?", String.class, pending)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from users where id = ? and deleted_at is not null", Integer.class, a.id())).isEqualTo(1);

        // an admin can see the account was deleted but can't bring it back
        JsonNode row = null;
        for (JsonNode u : admin.get("/api/admin/users").body()) if (u.get("id").asLong() == a.id()) row = u;
        assertThat(row).isNotNull();
        assertThat(row.get("deleted").asBoolean()).isTrue();
        assertThat(admin.patch("/api/admin/users/" + a.id(), Map.of("enabled", true)).code()).isEqualTo("ACCOUNT_DELETED");
        assertThat(admin.patch("/api/admin/users/" + a.id(), Map.of("role", "ADMIN")).code()).isEqualTo("ACCOUNT_DELETED");
        // and no reset link can bring it back either
        assertThat(admin.post("/api/admin/users/" + a.id() + "/password-reset", null).code()).isEqualTo("ACCOUNT_DELETED");

        // the name is free again, and the newcomer inherits nothing
        Response again = client().post("/api/auth/register", registration(invite(1), a.username(), PASSWORD));
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.body().get("id").asLong()).isNotEqualTo(a.id());
        assertThat(b.client().get("/api/users/" + a.username() + "/stats").body().get("matchesPlayed").asInt()).isZero();
    }

    @Test
    void theLastAdminCannotDeleteThemselves() {
        List<Long> others = jdbc.queryForList("select id from users where role = 'ADMIN' and enabled and id <> ?", Long.class, adminId);
        try {
            others.forEach(id -> jdbc.update("update users set role = 'USER' where id = ?", id));
            assertThat(admin.delete("/api/users/me", Map.of("password", ADMIN_PASSWORD)).code()).isEqualTo("LAST_ADMIN");
            assertThat(admin.get("/api/auth/me").status()).isEqualTo(200);
        } finally {
            others.forEach(id -> jdbc.update("update users set role = 'ADMIN' where id = ?", id));
        }
    }
}
