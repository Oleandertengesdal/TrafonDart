package no.trafondart.it;

import no.trafondart.it.ApiClient.Raw;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Malformed requests, security headers and error bodies. Nothing should ever produce a 500 or leak internals. */
class RobustnessIT extends IntegrationTestBase {

    @Test
    void securityHeadersAreOnEveryApiResponse() {
        Member m = member("hdr");
        for (Raw r : new Raw[]{m.client().raw("GET", "/api/auth/me", null, null), client().raw("GET", "/api/auth/me", null, null),
                client().raw("GET", "/api/auth/csrf", null, null)}) {
            assertThat(r.header("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(r.header("X-Frame-Options")).isEqualTo("DENY");
            assertThat(r.header("Content-Security-Policy")).contains("default-src 'none'").contains("frame-ancestors 'none'");
            assertThat(r.header("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(r.header("Cache-Control")).contains("no-store");
        }
        Raw me = m.client().raw("GET", "/api/auth/me", null, null);
        assertThat(me.header("Content-Type")).startsWith("application/json");
        assertThat(me.header("Server")).isNull();
        assertThat(me.header("X-Powered-By")).isNull();
    }

    @Test
    void malformedBodiesAreCleanBadRequests() {
        Member m = member("bad");
        String[][] cases = {
                {"POST", "/api/matches", "{not json", "application/json"},
                {"POST", "/api/matches", "[]", "application/json"},
                {"POST", "/api/matches", "{\"player1Id\":\"abc\"}", "application/json"},
                {"POST", "/api/matches", "{\"player1Id\":1e999}", "application/json"},
                {"PATCH", "/api/users/me", "{\"realName\":[1,2,3]}", "application/json"},
                {"POST", "/api/auth/login", "", "application/json"},
                {"POST", "/api/auth/login", "{\"username\":null,\"password\":null}", "application/json"},
                {"POST", "/api/auth/login", "{\"username\":\"" + "u".repeat(10_000) + "\",\"password\":\"x\"}", "application/json"},
        };
        for (String[] c : cases) {
            Raw r = m.client().raw(c[0], c[1], c[2], c[3]);
            assertThat(r.status()).as("%s %s %s", c[0], c[1], c[2].length() > 60 ? c[2].substring(0, 60) : c[2]).isEqualTo(400);
            assertThat(r.body()).contains("VALIDATION_FAILED");
            assertNoLeak(r.body());
        }
    }

    @Test
    void wrongContentTypeIsRefusedWithoutAStackTrace() {
        Member m = member("ctype");
        Raw form = m.client().raw("POST", "/api/matches", "player1Id=1", "application/x-www-form-urlencoded");
        assertThat(form.status()).isIn(400, 415);
        assertNoLeak(form.body());
        Raw xml = m.client().raw("POST", "/api/auth/login", "<login/>", "application/xml");
        assertThat(xml.status()).isIn(400, 415);
        assertNoLeak(xml.body());
        Raw text = m.client().raw("PATCH", "/api/users/me", "hi", "text/plain");
        assertThat(text.status()).isIn(400, 415);
        assertNoLeak(text.body());
    }

    @Test
    void hugeRequestBodiesAreRejected() {
        Member m = member("huge");
        String big = "{\"body\":\"" + "x".repeat(3_000_000) + "\"}";
        Raw r = m.client().raw("POST", "/api/matches/" + java.util.UUID.randomUUID() + "/comments", big, "application/json");
        assertThat(r.status()).isBetween(400, 499);
        assertNoLeak(r.body());
    }

    @Test
    void injectionLookingInputIsJustData() {
        Member m = member("inj");
        for (String evil : new String[]{"' OR 1=1 --", "admin'--", "\"; DROP TABLE users; --", "${jndi:ldap://x}", "%00", "../../etc/passwd"}) {
            Raw r = m.client().raw("GET", "/api/matches/recent?user=" + java.net.URLEncoder.encode(evil, java.nio.charset.StandardCharsets.UTF_8), null, null);
            assertThat(r.status()).as(evil).isIn(400, 404);
            assertNoLeak(r.body());
            Raw login = client().raw("POST", "/api/auth/login",
                    "{\"username\":\"" + evil.replace("\"", "\\\"") + "\",\"password\":\"" + evil.replace("\"", "\\\"") + "\"}", "application/json");
            assertThat(login.status()).as(evil).isIn(400, 401);
            assertNoLeak(login.body());
        }
        // the admin user still exists and works after all that
        assertThat(client().login("admin", ADMIN_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void errorBodiesHaveAStableShape() {
        Raw r = client().raw("GET", "/api/matches/live", null, null);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.body()).isEqualTo("{\"code\":\"AUTH_REQUIRED\",\"status\":401}");
        Member m = member("shape");
        Raw notFound = m.client().raw("GET", "/api/users/nobody_at_all/stats", null, null);
        assertThat(notFound.body()).contains("\"code\":\"NOT_FOUND\"").contains("\"status\":404");
        assertNoLeak(notFound.body());
    }
}
