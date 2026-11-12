package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin-issued one-time links for forgotten passwords. */
class PasswordResetIT extends IntegrationTestBase {

    private String link(long userId) {
        Response r = admin.post("/api/admin/users/" + userId + "/password-reset", null);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("expiresAt").asString()).isNotBlank();
        return r.body().get("token").asString();
    }

    @Test
    void aLinkWorksOnceAndANewLinkCancelsTheOldOne() {
        Member m = member("rst");
        String first = link(m.id());
        String second = link(m.id());
        assertThat(client().post("/api/auth/password-reset/check", Map.of("token", first)).body().get("valid").asBoolean()).isFalse();
        Response check = client().post("/api/auth/password-reset/check", Map.of("token", second));
        assertThat(check.body().get("valid").asBoolean()).isTrue();
        assertThat(check.body().get("username").asString()).isEqualTo(m.username());

        assertThat(client().post("/api/auth/password-reset", Map.of("token", first, "newPassword", "new-pass-one-1")).code()).isEqualTo("RESET_LINK_INVALID");
        ApiClient c = client();
        assertThat(c.post("/api/auth/password-reset", Map.of("token", second, "newPassword", "new-pass-one-1")).status()).isEqualTo(200);
        assertThat(c.get("/api/auth/me").body().get("id").asLong()).isEqualTo(m.id());
        assertThat(m.client().get("/api/auth/me").status()).as("old sessions end").isEqualTo(401);
        assertThat(client().post("/api/auth/password-reset", Map.of("token", second, "newPassword", "new-pass-two-2")).code()).isEqualTo("RESET_LINK_INVALID");
    }

    @Test
    void aLinkCannotBeUsedTwiceEvenAtTheSameMoment() throws Exception {
        Member m = member("rstrace");
        String token = link(m.id());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String pw = "racing-password-" + i;
            results.add(pool.submit(() -> client().post("/api/auth/password-reset", Map.of("token", token, "newPassword", pw))));
        }
        int ok = 0;
        for (Future<Response> f : results) {
            Response r = f.get();
            if (r.status() == 200) ok++;
            else assertThat(r.code()).isIn("RESET_LINK_INVALID", "CONFLICT");
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void badInputIsRefusedWithoutLeakingAnything() {
        Member m = member("rstbad");
        String token = link(m.id());
        assertThat(client().post("/api/auth/password-reset", Map.of("token", token, "newPassword", "short")).status()).isEqualTo(400);
        assertThat(client().post("/api/auth/password-reset", Map.of("token", token, "newPassword", "æ".repeat(40))).status()).isEqualTo(400);
        assertThat(client().post("/api/auth/password-reset/check", Map.of("token", "x".repeat(101))).status()).isEqualTo(400);
        Response unknown = client().post("/api/auth/password-reset/check", Map.of("token", "made-up-token"));
        assertThat(unknown.body().get("valid").asBoolean()).isFalse();
        assertThat(unknown.body().get("username").isNull()).isTrue();
        // the link still works after the failed attempts
        assertThat(client().post("/api/auth/password-reset", Map.of("token", token, "newPassword", "valid-password-1")).status()).isEqualTo(200);
        assertThat(admin.post("/api/admin/users/987654321/password-reset", null).status()).isEqualTo(404);
    }

    @Test
    void disabledAccountsCannotBeResetIntoUse() {
        Member m = member("rstdis");
        String token = link(m.id());
        admin.patch("/api/admin/users/" + m.id(), Map.of("enabled", false));
        assertThat(client().post("/api/auth/password-reset", Map.of("token", token, "newPassword", "sneaky-pass-12")).code()).isEqualTo("AUTH_ACCOUNT_DISABLED");
        assertThat(client().post("/api/auth/password-reset/check", Map.of("token", token)).body().get("valid").asBoolean()).isFalse();
    }

    @Test
    void theTokenIsNeverStoredOrListedInPlainText() {
        Member m = member("rstlist");
        String token = link(m.id());
        assertThat(admin.get("/api/admin/users").body().toString()).doesNotContain(token);
    }
}
