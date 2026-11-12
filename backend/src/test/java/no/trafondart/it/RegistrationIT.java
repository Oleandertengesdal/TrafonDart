package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Invite codes and sign-up: every way a code can be wrong, and every way a sign-up form can be abused. */
class RegistrationIT extends IntegrationTestBase {

    @Test
    void unknownExpiredRevokedAndUsedUpCodesAreRefusedWithTheRightReason() {
        assertThat(client().post("/api/auth/register", registration("AAAA-BBBB-CCCC", unique("x"), PASSWORD)).code()).isEqualTo("INVITE_INVALID");

        Response created = admin.post("/api/admin/invites", Map.of("maxUses", 5, "validHours", 1));
        String code = created.body().get("code").asString();
        long id = created.body().get("invite").get("id").asLong();
        assertThat(admin.post("/api/admin/invites/" + id + "/revoke", null).status()).isEqualTo(204);
        assertThat(client().post("/api/auth/register", registration(code, unique("x"), PASSWORD)).code()).isEqualTo("INVITE_REVOKED");
        assertThat(client().get("/api/auth/invites/" + code).body().get("reason").asString()).isEqualTo("INVITE_REVOKED");

        String once = invite(1);
        assertThat(client().post("/api/auth/register", registration(once, unique("ok"), PASSWORD)).status()).isEqualTo(200);
        assertThat(client().post("/api/auth/register", registration(once, unique("late"), PASSWORD)).code()).isEqualTo("INVITE_USED_UP");
    }

    @Test
    void aFailedSignUpDoesNotUseUpTheInvite() {
        String code = invite(1);
        Member existing = member("taken");
        assertThat(client().post("/api/auth/register", registration(code, existing.username().toUpperCase(), PASSWORD)).code())
                .as("usernames are unique regardless of case").isEqualTo("USERNAME_TAKEN");
        assertThat(client().post("/api/auth/register", registration(code, unique("fresh"), PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void concurrentSignUpsCanNeverExceedTheInviteLimit() throws Exception {
        String code = invite(2);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String name = unique("race");
            Callable<Response> job = () -> client().post("/api/auth/register", registration(code, name, PASSWORD));
            results.add(pool.submit(job));
        }
        int ok = 0;
        for (Future<Response> f : results) {
            Response r = f.get();
            if (r.status() == 200) ok++;
            else assertThat(r.code()).isIn("INVITE_USED_UP", "CONFLICT");
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(2);
        JsonNodeAssert.inviteUses(admin, code, 2);
    }

    @Test
    void sameUsernameRegisteredTwiceAtOnceGivesOneAccount() throws Exception {
        String code = invite(5);
        String name = unique("twin");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> client().post("/api/auth/register", registration(code, name, PASSWORD))));
        }
        int ok = 0;
        for (Future<Response> f : results) {
            Response r = f.get();
            if (r.status() == 200) ok++;
            else {
                assertThat(r.status()).as("never a 500: %s", r.body()).isLessThan(500);
                assertThat(r.code()).isIn("USERNAME_TAKEN", "CONFLICT");
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void usernamesAndNamesAreValidated() {
        String code = invite(20);
        for (String bad : new String[]{"ab", "x".repeat(25), "has space", "semi;colon", "<script>", "ø_not_ascii", "a/b", "", "../admin"}) {
            Response r = client().post("/api/auth/register", registration(code, bad, PASSWORD));
            assertThat(r.status()).as("username '%s'", bad).isEqualTo(400);
            assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
        }
        for (String bad : new String[]{"<b>Bold</b>", "Robert'); DROP TABLE users;--", "  ", "1337", "x".repeat(61), "Name\u0000", "Name\nNewline"}) {
            Map<String, Object> reg = registration(code, unique("nm"), PASSWORD);
            reg.put("realName", bad);
            assertThat(client().post("/api/auth/register", reg).status()).as("real name '%s'", bad).isEqualTo(400);
        }
        Map<String, Object> nordic = registration(code, unique("nordic"), PASSWORD);
        nordic.put("realName", "  Åsmund Ødegård-Æsir  ");
        Response ok = client().post("/api/auth/register", nordic);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("realName").asString()).isEqualTo("Åsmund Ødegård-Æsir");
    }

    @Test
    void passwordsMustBeLongEnough() {
        String code = invite(5);
        assertThat(client().post("/api/auth/register", registration(code, unique("pw"), "short")).code()).isEqualTo("VALIDATION_FAILED");
        assertThat(client().post("/api/auth/register", registration(code, unique("pw"), "          ")).status()).isEqualTo(400);
        assertThat(client().post("/api/auth/register", registration(code, unique("pw"), "exactly-10")).status()).isEqualTo(200);
    }

    @Test
    void signUpCannotChooseItsOwnRoleOrRating() {
        String code = invite(1);
        Map<String, Object> reg = registration(code, unique("sneaky"), PASSWORD);
        reg.put("role", "ADMIN");
        reg.put("rating", 3000);
        reg.put("enabled", true);
        reg.put("id", 1);
        Response r = client().post("/api/auth/register", reg);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("role").asString()).isEqualTo("USER");
        assertThat(r.body().get("rating").asInt()).isEqualTo(1500);
        assertThat(r.body().get("id").asLong()).isNotEqualTo(1L);
    }

    @Test
    void invalidLocaleIsRejectedAndMissingLocaleDefaultsSensibly() {
        String code = invite(2);
        Map<String, Object> reg = registration(code, unique("loc"), PASSWORD);
        reg.put("preferredLocale", "de");
        assertThat(client().post("/api/auth/register", reg).status()).isEqualTo(400);
        reg.remove("preferredLocale");
        Response ok = client().post("/api/auth/register", reg);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("preferredLocale").asString()).isIn("nb", "en");
    }

    @Test
    void inviteCheckIsCaseAndDashInsensitiveButRevealsNothingForBadCodes() {
        String code = invite(1);
        Response sloppy = client().get("/api/auth/invites/" + code.replace("-", "").toLowerCase());
        assertThat(sloppy.body().get("valid").asBoolean()).isTrue();
        Response bad = client().get("/api/auth/invites/ZZZZ-ZZZZ-ZZZZ");
        assertThat(bad.body().get("valid").asBoolean()).isFalse();
        assertThat(bad.body().get("expiresAt").isNull()).isTrue();
        Response huge = client().get("/api/auth/invites/" + "A".repeat(300));
        assertThat(huge.status()).isEqualTo(200);
        assertThat(huge.body().get("valid").asBoolean()).isFalse();
    }

    @Test
    void adminInviteSettingsAreBounded() {
        assertThat(admin.post("/api/admin/invites", Map.of("maxUses", 0, "validHours", 24)).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/invites", Map.of("maxUses", 101, "validHours", 24)).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 0)).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 90 * 24 + 1)).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 24, "note", "x".repeat(101))).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 24, "note", "evil\u0000note")).status()).isEqualTo(400);
        assertThat(admin.post("/api/admin/invites/999999999/revoke", null).status()).isEqualTo(404);
        // the listing never contains a usable code
        String code = invite(1);
        assertThat(admin.get("/api/admin/invites").body().toString()).doesNotContain(code).doesNotContain(code.replace("-", ""));
    }

    /** Small helper kept here so the test reads top-down. */
    static final class JsonNodeAssert {
        static void inviteUses(ApiClient admin, String code, int expected) {
            String hint = code.replace("-", "").substring(0, 4);
            var list = admin.get("/api/admin/invites").body();
            for (var i : list) {
                if (i.get("hint").asString().startsWith(hint)) {
                    assertThat(i.get("uses").asInt()).isEqualTo(expected);
                    assertThat(i.get("usedBy").size()).isEqualTo(expected);
                    return;
                }
            }
            throw new AssertionError("invite not listed");
        }
    }
}
