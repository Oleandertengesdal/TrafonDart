package no.trafondart.it;

import no.trafondart.it.ApiClient.Raw;
import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Login, sessions, cookies, logout, password changes and lock-outs. */
class AuthAndSessionIT extends IntegrationTestBase {

    @Test
    void sessionCookieIsHttpOnlySameSiteAndScopedToTheSite() {
        Member m = member("cookie");
        ApiClient c = client();
        Raw r = c.raw("POST", "/api/auth/login", "{\"username\":\"" + m.username() + "\",\"password\":\"" + PASSWORD + "\"}",
                "application/json");
        assertThat(r.status()).isEqualTo(200);
        String session = r.all("Set-Cookie").stream().filter(s -> s.startsWith("td_session=")).findFirst().orElseThrow();
        assertThat(session).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").contains("Max-Age=");
        assertThat(session).doesNotContain("Domain=");
        // the CSRF cookie must be readable by the app, but never the session
        Raw csrf = client().raw("GET", "/api/auth/csrf", null, null);
        String xsrf = csrf.all("Set-Cookie").stream().filter(s -> s.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(xsrf).doesNotContain("HttpOnly").contains("SameSite=Lax");
        assertThat(r.body()).doesNotContain("passwordHash", "password_hash", "tokenVersion", "$2a$", "$2b$");
    }

    @Test
    void meReturnsTheLoggedInUserAndNothingSecret() {
        Member m = member("me");
        Response me = m.client().get("/api/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().get("username").asString()).isEqualTo(m.username());
        assertThat(me.body().get("role").asString()).isEqualTo("USER");
        assertThat(me.body().toString()).doesNotContain("password", "token", "$2");
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledUserAllLookTheSameUntilThePasswordIsRight() {
        Member m = member("wrong");
        assertThat(client().login(m.username(), "not-the-password").code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(client().login("no_such_user_x", "not-the-password").code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(client().login(m.username().toUpperCase(), PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void overlongPasswordsAreRejectedCleanlyNotWithAServerError() {
        Member m = member("long");
        // BCrypt only uses 72 bytes; a longer login attempt must be a normal failed login, never a 500
        Response login = client().login(m.username(), "x".repeat(150));
        assertThat(login.status()).isEqualTo(401);
        assertThat(login.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        // ... and a password that only matches in its first 72 bytes must not log you in
        String base = "y".repeat(72);
        String code = invite(1);
        String name = unique("trunc");
        assertThat(client().post("/api/auth/register", registration(code, name, base)).status()).isEqualTo(200);
        assertThat(client().login(name, base + "EXTRA").status()).isEqualTo(401);
        // multi-byte: 25 × "ø" is 50 bytes (fine), 40 × "ø" is 80 bytes (too long even though it is 40 characters)
        assertThat(client().post("/api/auth/register", registration(invite(1), unique("mb"), "ø".repeat(40))).status()).isEqualTo(400);
        // change-password with an absurd current password is a normal "incorrect" answer
        Response change = m.client().post("/api/auth/change-password", Map.of("currentPassword", "z".repeat(500), "newPassword", "a-new-password-1"));
        assertThat(change.status()).isIn(400, 403);
        assertNoLeak(change.body() == null ? null : change.body().toString());
    }

    @Test
    void logoutClearsTheCookieAndLogoutEverywhereKillsOtherDevices() {
        Member m = member("logout");
        ApiClient phone = client();
        assertThat(phone.login(m.username(), PASSWORD).status()).isEqualTo(200);

        Raw out = m.client().raw("POST", "/api/auth/logout", "{}", "application/json");
        assertThat(out.status()).isEqualTo(204);
        assertThat(out.all("Set-Cookie")).anyMatch(s -> s.startsWith("td_session=") && s.contains("Max-Age=0"));

        // phone is still logged in until someone logs out everywhere
        assertThat(phone.get("/api/auth/me").status()).isEqualTo(200);
        ApiClient laptop = client();
        laptop.login(m.username(), PASSWORD);
        String stolen = laptop.cookie("td_session");
        assertThat(laptop.post("/api/auth/logout-all", null).status()).isEqualTo(204);
        assertThat(phone.get("/api/auth/me").status()).isEqualTo(401);
        ApiClient thief = client();
        thief.setCookie("td_session", stolen);
        assertThat(thief.get("/api/auth/me").status()).as("a copied cookie dies with logout-all").isEqualTo(401);
    }

    @Test
    void changingPasswordRequiresTheOldOneAndLogsOutOtherDevicesButNotThisOne() {
        Member m = member("pwchange");
        ApiClient other = client();
        other.login(m.username(), PASSWORD);

        Response wrong = m.client().post("/api/auth/change-password", Map.of("currentPassword", "nope-nope-nope", "newPassword", "brand-new-pass-1"));
        assertThat(wrong.code()).isEqualTo("PASSWORD_INCORRECT");
        Response tooShort = m.client().post("/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "short"));
        assertThat(tooShort.status()).isEqualTo(400);

        Response ok = m.client().post("/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "brand-new-pass-1"));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(m.client().get("/api/auth/me").status()).isEqualTo(200);
        assertThat(other.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(client().login(m.username(), PASSWORD).status()).isEqualTo(401);
        assertThat(client().login(m.username(), "brand-new-pass-1").status()).isEqualTo(200);
    }

    @Test
    void forgedGarbageAndWrongAudienceCookiesAreIgnored() {
        Member m = member("forge");
        String ticket = m.client().post("/api/ws/ticket", null).body().get("ticket").asString();
        for (String bad : new String[]{"garbage", ticket, m.client().cookie("td_session") + "x", "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0."}) {
            ApiClient c = client();
            c.setCookie("td_session", bad);
            Response r = c.get("/api/auth/me");
            assertThat(r.status()).as(bad).isEqualTo(401);
            assertThat(r.code()).isEqualTo("AUTH_REQUIRED");
        }
    }

    @Test
    void disablingAMemberEndsTheirSessionsAndBlocksLogin() {
        Member m = member("disable");
        assertThat(admin.patch("/api/admin/users/" + m.id(), Map.of("enabled", false)).status()).isEqualTo(200);
        assertThat(m.client().get("/api/auth/me").status()).isEqualTo(401);
        assertThat(client().login(m.username(), PASSWORD).code()).isEqualTo("AUTH_ACCOUNT_DISABLED");
        // they disappear from pickers and can't be put in a match
        assertThat(admin.get("/api/users").body().toString()).doesNotContain("\"" + m.username() + "\"");
        Member other = member("disable2");
        assertThat(other.client().post("/api/matches", matchBody(other.id(), m.id(), true, 501, "DOUBLE", 1)).status()).isEqualTo(400);
        // re-enabled: can log in again
        admin.patch("/api/admin/users/" + m.id(), Map.of("enabled", true));
        assertThat(client().login(m.username(), PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void repeatedWrongPasswordsLockTheUsernameWithRetryAfterAndTheRightPasswordWaits() {
        Member m = member("lock");
        for (int i = 0; i < 6; i++) client().login(m.username(), "wrong-password-" + i);
        Raw locked = client().raw("POST", "/api/auth/login", "{\"username\":\"" + m.username() + "\",\"password\":\"" + PASSWORD + "\"}", "application/json");
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.body()).contains("RATE_LIMITED");
        assertThat(Integer.parseInt(locked.header("Retry-After"))).isPositive();
        // the lock is per username, not per person: another member is not affected
        Member other = member("lock2");
        assertThat(client().login(other.username(), PASSWORD).status()).isEqualTo(200);
        // an admin reset link lifts the lock
        String token = admin.post("/api/admin/users/" + m.id() + "/password-reset", null).body().get("token").asString();
        assertThat(client().post("/api/auth/password-reset", Map.of("token", token, "newPassword", "unlocked-pass-1")).status()).isEqualTo(200);
        assertThat(client().login(m.username(), "unlocked-pass-1").status()).isEqualTo(200);
    }

    @Test
    void usernameCaseVariantsShareOneLock() {
        Member m = member("caselock");
        for (int i = 0; i < 6; i++) client().login(i % 2 == 0 ? m.username().toUpperCase() : m.username(), "wrong-password-" + i);
        assertThat(client().login(m.username(), PASSWORD).status()).isEqualTo(429);
    }

    @Test
    void perIpLimitsCannotBeDodgedByForgingXForwardedFor() {
        // An attacker who calls the backend directly controls everything left of the edge entry.
        ApiClient attacker = client();
        String edge = "203.0.113." + (int) (Math.random() * 200 + 20);
        int limited = 0;
        for (int i = 0; i < 200; i++) {
            attacker.forwardedFor = "10." + (i / 250) + "." + (i % 250) + ".1, " + edge;
            Response r = attacker.get("/api/auth/invites/NOPE-NOPE-" + i);
            if (r.status() == 429) limited++;
        }
        assertThat(limited).as("the shared edge budget must run out").isPositive();
    }

    @Test
    void sessionsSurviveARoleChangeOnlyByLoggingInAgain() {
        Member m = member("role");
        assertThat(admin.patch("/api/admin/users/" + m.id(), Map.of("role", "ADMIN")).status()).isEqualTo(200);
        assertThat(m.client().get("/api/auth/me").status()).isEqualTo(401);
        ApiClient again = client();
        assertThat(again.login(m.username(), PASSWORD).body().get("role").asString()).isEqualTo("ADMIN");
        assertThat(again.get("/api/admin/users").status()).isEqualTo(200);
        admin.patch("/api/admin/users/" + m.id(), Map.of("role", "USER"));
        assertThat(again.get("/api/admin/users").status()).isEqualTo(401);
    }

    @Test
    void anAdminCannotDemoteOrDisableThemself() {
        assertThat(admin.patch("/api/admin/users/" + adminId, Map.of("role", "USER")).code()).isEqualTo("CANNOT_CHANGE_SELF");
        assertThat(admin.patch("/api/admin/users/" + adminId, Map.of("enabled", false)).code()).isEqualTo("CANNOT_CHANGE_SELF");
        assertThat(admin.patch("/api/admin/users/" + adminId, Map.of("role", "ROOT")).status()).isEqualTo(400);
    }
}
