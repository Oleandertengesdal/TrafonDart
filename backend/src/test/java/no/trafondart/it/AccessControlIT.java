package no.trafondart.it;

import no.trafondart.it.ApiClient.Raw;
import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walks every REST endpoint the application actually exposes (read from Spring's handler mapping, so a new
 * endpoint is covered automatically) and checks authentication, CSRF and admin-only access.
 */
class AccessControlIT extends IntegrationTestBase {

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    record Endpoint(String method, String path, String template) {
        boolean isPublic() {
            return PUBLIC.contains(method + " " + template);
        }
    }

    static final Set<String> PUBLIC = Set.of(
            "GET /api/auth/csrf", "POST /api/auth/login", "POST /api/auth/register", "POST /api/auth/logout",
            "POST /api/auth/password-reset", "POST /api/auth/password-reset/check", "GET /api/auth/invites/{code}",
            "POST /api/client-errors");

    List<Endpoint> endpoints() {
        List<Endpoint> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            if (info.getPathPatternsCondition() == null) continue;
            for (String template : info.getPathPatternsCondition().getPatternValues()) {
                if (!template.startsWith("/api/")) continue;
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (RequestMethod m : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    out.add(new Endpoint(m.name(), fill(template), template));
                }
            }
        }
        assertThat(out).as("the walk must find the API").hasSizeGreaterThan(40);
        return out;
    }

    private static String fill(String template) {
        return template
                .replace("{id}", template.contains("/matches/{id}") || template.contains("/tournaments/{id}") ? UUID.randomUUID().toString() : "987654")
                .replace("{fixtureId}", "987654")
                .replace("{username}", "nobody_here")
                .replace("{other}", "nobody_else")
                .replace("{code}", "AAAA-BBBB-CCCC")
                .replaceAll("\\{[A-Za-z]*Id}", "987654")
                .replaceAll("\\{[^}]*}", "987654");
    }

    private static Raw call(ApiClient c, Endpoint e) {
        boolean body = !e.method().equals("GET") && !e.method().equals("DELETE");
        return c.raw(e.method(), e.path(), body ? "{}" : null, body ? "application/json" : null);
    }

    @Test
    void everyNonPublicEndpointRequiresLogin() {
        ApiClient anonymous = client();
        for (Endpoint e : endpoints()) {
            if (e.isPublic()) continue;
            Raw r = call(anonymous, e);
            assertThat(r.status()).as("%s %s without login", e.method(), e.template()).isEqualTo(401);
            assertThat(r.body()).contains("AUTH_REQUIRED");
        }
    }

    @Test
    void everyStateChangingEndpointRequiresTheCsrfToken() {
        Member m = member("csrf");
        m.client().sendCsrf = false;
        for (Endpoint e : endpoints()) {
            if (e.method().equals("GET")) continue;
            Raw r = call(m.client(), e);
            assertThat(r.status()).as("%s %s without CSRF", e.method(), e.template()).isEqualTo(403);
            assertThat(r.body()).contains("CSRF_INVALID");
        }
    }

    @Test
    void aTokenFromAnotherBrowserOrWithoutTheCookieIsRejected() {
        Member m = member("csrf2");
        ApiClient other = client();
        other.get("/api/auth/csrf");
        String foreign = other.cookie("XSRF-TOKEN");
        m.client().sendCsrf = false;
        Raw r = m.client().raw("PATCH", "/api/users/me", "{\"preferredLocale\":\"en\"}", "application/json", "X-XSRF-TOKEN", foreign);
        assertThat(r.status()).isEqualTo(403);
        // header present, cookie removed (what a cross-site attacker would have)
        String mine = m.client().cookie("XSRF-TOKEN");
        ApiClient attacker = client();
        attacker.setCookie("td_session", m.client().cookie("td_session"));
        attacker.sendCsrf = false;
        Raw r2 = attacker.raw("PATCH", "/api/users/me", "{\"preferredLocale\":\"en\"}", "application/json", "X-XSRF-TOKEN", mine);
        assertThat(r2.status()).isEqualTo(403);
    }

    @Test
    void everyAdminEndpointIsForbiddenForMembers() {
        Member m = member("notadmin");
        for (Endpoint e : endpoints()) {
            if (!e.template().startsWith("/api/admin/")) continue;
            Raw r = call(m.client(), e);
            assertThat(r.status()).as("%s %s as member", e.method(), e.template()).isEqualTo(403);
            assertThat(r.body()).contains("FORBIDDEN");
        }
    }

    @Test
    void unknownPathsAndMethodsAreDeniedWithoutDetails() {
        Member m = member("unknown");
        Raw missing = m.client().raw("GET", "/api/does-not-exist", null, null);
        assertThat(missing.status()).isEqualTo(404);
        assertNoLeak(missing.body());
        Raw outside = m.client().raw("GET", "/secret.txt", null, null);
        assertThat(outside.status()).isIn(401, 403, 404);
        Raw wrongMethod = m.client().raw("DELETE", "/api/users/leaderboard", null, null);
        assertThat(wrongMethod.status()).isEqualTo(405);
        assertNoLeak(wrongMethod.body());
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/configprops", "/actuator/loggers"}) {
            assertThat(admin.raw("GET", path, null, null).status()).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void healthIsPublicAndRevealsNothingButUp() {
        Raw r = client().raw("GET", "/actuator/health", null, null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("\"status\":\"UP\"").doesNotContain("components", "db", "diskSpace", "details");
    }

    @Test
    void membersCanOnlyChangeTheirOwnProfileAndNotTheirRole() {
        Member m = member("self");
        Response r = m.client().patch("/api/users/me", Map.of("realName", "New Name", "role", "ADMIN", "id", adminId, "rating", 9999));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("id").asLong()).isEqualTo(m.id());
        assertThat(r.body().get("role").asString()).isEqualTo("USER");
        assertThat(r.body().get("rating").asInt()).isEqualTo(1500);
        assertThat(admin.get("/api/auth/me").body().get("realName").asString()).isNotEqualTo("New Name");
        assertThat(m.client().patch("/api/users/me", Map.of("realName", "<img src=x onerror=alert(1)>")).status()).isEqualTo(400);
        assertThat(m.client().patch("/api/users/me", Map.of("realName", "")).status()).isEqualTo(400);
        assertThat(m.client().patch("/api/users/me", Map.of("preferredLocale", "xx")).status()).isEqualTo(400);
    }

    @Test
    void outsidersCannotScoreAbandonOrDisputeOtherPeoplesMatches() {
        Member a = member("ownA"), b = member("ownB"), outsider = member("outsdr");
        String id = quickMatch(a.client(), a.id(), b.id(), true);
        Response t = throwDart(outsider.client(), id, 20, 3, 0);
        assertThat(t.code()).isEqualTo("MATCH_NOT_SCORER");
        assertThat(outsider.client().post("/api/matches/" + id + "/undo", Map.of("expectedDartCount", 1)).code()).isEqualTo("MATCH_NOT_SCORER");
        assertThat(outsider.client().post("/api/matches/" + id + "/abandon", null).code()).isEqualTo("MATCH_NOT_SCORER");
        // the opponent may score, the admin may score
        assertThat(throwDart(b.client(), id, 20, 3, 0).status()).isEqualTo(200);
        assertThat(throwDart(admin, id, 20, 3, 1).status()).isEqualTo(200);
        playToEnd(a.client(), id, 0);
        assertThat(outsider.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "I was not there")).code())
                .isEqualTo("DISPUTE_NOT_ALLOWED");
    }

    @Test
    void ratedMatchesCanOnlyBeCreatedByAPlayerOrAnAdmin() {
        Member a = member("ratA"), b = member("ratB"), c = member("ratC");
        assertThat(c.client().post("/api/matches", matchBody(a.id(), b.id(), true, 501, "DOUBLE", 1)).status()).isEqualTo(403);
        assertThat(c.client().post("/api/matches", matchBody(a.id(), b.id(), false, 501, "DOUBLE", 1)).status()).isEqualTo(201);
        assertThat(admin.post("/api/matches", matchBody(a.id(), b.id(), true, 501, "DOUBLE", 1)).status()).isEqualTo(201);
        // omitting "rated" means rated
        Map<String, Object> body = matchBody(a.id(), b.id(), true, 501, "DOUBLE", 1);
        body.remove("rated");
        assertThat(c.client().post("/api/matches", body).status()).isEqualTo(403);
    }

    @Test
    void organisersWhoAreNotPlayingCannotProduceRatedResultsForOthers() {
        Member org = member("orgnsr"), p1 = member("trnP1"), p2 = member("trnP2");
        Response t = org.client().post("/api/tournaments", Map.of("name", unique("Cup"), "format", "SINGLE_ELIMINATION",
                "startScore", 301, "inRule", "STRAIGHT", "outRule", "STRAIGHT", "legsToWin", 1, "rated", true,
                "participantIds", List.of(p1.id(), p2.id())));
        assertThat(t.status()).isEqualTo(201);
        String tid = t.body().get("summary").get("id").asString();
        JsonNodes.ok(org.client().post("/api/tournaments/" + tid + "/start", null));
        long fixture = admin.get("/api/tournaments/" + tid).body().get("fixtures").get(0).get("id").asLong();
        // The organiser could otherwise start the fixture and then score it alone: a rated result nobody played.
        Response start = org.client().post("/api/tournaments/" + tid + "/fixtures/" + fixture + "/start", null);
        assertThat(start.status()).isEqualTo(403);
        // a player in the fixture can start it, and then the organiser isn't a scorer
        String matchId = p1.client().post("/api/tournaments/" + tid + "/fixtures/" + fixture + "/start", null).body().get("matchId").asString();
        assertThat(throwDart(org.client(), matchId, 20, 3, 0).code()).isEqualTo("MATCH_NOT_SCORER");
    }

    @Test
    void onlyTheOrganiserOrAnAdminManagesATournament() {
        Member org = member("org2"), x = member("org2x"), y = member("org2y");
        Response t = org.client().post("/api/tournaments", Map.of("name", unique("League"), "format", "ROUND_ROBIN",
                "startScore", 301, "inRule", "STRAIGHT", "outRule", "STRAIGHT", "legsToWin", 1, "rated", false,
                "participantIds", List.of(org.id(), x.id())));
        String tid = t.body().get("summary").get("id").asString();
        assertThat(x.client().put("/api/tournaments/" + tid + "/participants", Map.of("participantIds", List.of(x.id(), y.id()))).status()).isEqualTo(403);
        assertThat(x.client().post("/api/tournaments/" + tid + "/start", null).status()).isEqualTo(403);
        assertThat(x.client().post("/api/tournaments/" + tid + "/cancel", null).status()).isEqualTo(403);
        assertThat(admin.put("/api/tournaments/" + tid + "/participants", Map.of("participantIds", List.of(org.id(), x.id(), y.id()))).status()).isEqualTo(200);
    }

    /** Tiny assertion helper. */
    static final class JsonNodes {
        static void ok(Response r) {
            assertThat(r.status()).as("%s", r.body()).isBetween(200, 299);
        }
    }
}
