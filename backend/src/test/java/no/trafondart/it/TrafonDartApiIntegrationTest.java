package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end API test against a real PostgreSQL database: auth, invites, CSRF, scoring, live socket, ratings,
 * stats, tournaments and disputes. Run with {@code ./mvnw verify} (needs the database from docker-compose.yml).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrafonDartApiIntegrationTest {

    @TestConfiguration
    static class CleanDatabase {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return (Flyway flyway) -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Value("${local.server.port}")
    int port;

    ApiClient admin;
    ApiClient alice;
    long adminId;
    long aliceId;
    String matchId;

    @BeforeAll
    void setUp() {
        admin = new ApiClient(port);
        alice = new ApiClient(port);
    }

    @Test
    @Order(1)
    void unauthenticatedRequestsAreRejected() {
        Response r = new ApiClient(port).get("/api/matches/live");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo("AUTH_REQUIRED");
    }

    @Test
    @Order(2)
    void loginRequiresCsrfAndCorrectPassword() {
        ApiClient noCsrf = new ApiClient(port);
        noCsrf.sendCsrf = false;
        assertThat(noCsrf.login("admin", "test-admin-password").code()).isEqualTo("CSRF_INVALID");

        Response wrong = admin.login("admin", "wrong-password-123");
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        Response unknown = admin.login("nobody-here", "wrong-password-123");
        assertThat(unknown.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");

        Response ok = admin.login("ADMIN", "test-admin-password"); // usernames are case-insensitive
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("role").asString()).isEqualTo("ADMIN");
        adminId = ok.body().get("id").asLong();
        assertThat(admin.cookie("td_session")).isNotBlank();
    }

    @Test
    @Order(3)
    void invitesControlRegistration() {
        Response created = admin.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 24, "note", "Alice"));
        assertThat(created.status()).isEqualTo(201);
        String code = created.body().get("code").asString();
        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");

        assertThat(alice.get("/api/auth/invites/" + code.toLowerCase()).body().get("valid").asBoolean()).isTrue();

        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("inviteCode", code);
        reg.put("username", "alice");
        reg.put("realName", "Alice Ødegård");
        reg.put("password", "alice-password-1");
        reg.put("preferredLocale", "nb");
        Response registered = alice.post("/api/auth/register", reg);
        assertThat(registered.status()).isEqualTo(200);
        aliceId = registered.body().get("id").asLong();

        ApiClient bob = new ApiClient(port);
        reg.put("username", "bob");
        assertThat(bob.post("/api/auth/register", reg).code()).isEqualTo("INVITE_USED_UP");
        reg.put("inviteCode", "AAAA-BBBB-CCCC");
        assertThat(bob.post("/api/auth/register", reg).code()).isEqualTo("INVITE_INVALID");

        Response weak = bob.post("/api/auth/register", Map.of("inviteCode", code, "username", "b",
                "realName", "B", "password", "short"));
        assertThat(weak.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(weak.body().get("fieldErrors").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(4)
    void normalUsersCannotUseAdminEndpoints() {
        assertThat(alice.get("/api/admin/users").status()).isEqualTo(403);
        assertThat(alice.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 1)).status()).isEqualTo(403);
    }

    @Test
    @Order(5)
    void scoringAMatchUpdatesStateRatingsAndStats() throws Exception {
        Response created = admin.post("/api/matches", Map.of("player1Id", adminId, "player2Id", aliceId,
                "startScore", 301, "inRule", "STRAIGHT", "outRule", "DOUBLE", "legsToWin", 1, "startingPlayer", 0));
        assertThat(created.status()).isEqualTo(201);
        matchId = created.body().get("id").asString();

        // Live socket: connect with a ticket and subscribe before scoring.
        StompProbe probe = StompProbe.connect(port, admin.post("/api/ws/ticket", null).body().get("ticket").asString());
        probe.subscribe("/topic/matches/" + matchId);

        int n = 0;
        n = dart(20, 3, n); n = dart(20, 3, n); n = dart(20, 3, n);             // admin 180 -> 121
        JsonNode pushed = probe.nextMessage();
        assertThat(pushed.get("dartCount").asInt()).isGreaterThan(0);

        n = dart(1, 1, n); n = dart(1, 1, n); n = dart(1, 1, n);                // alice 3
        n = dart(20, 3, n); n = dart(20, 1, n);                                  // admin 121 -> 41

        // Out-of-sync submissions are rejected with the current state attached.
        Response stale = admin.post("/api/matches/" + matchId + "/throws", Map.of("segment", 1, "multiplier", 1, "expectedDartCount", 3));
        assertThat(stale.code()).isEqualTo("MATCH_OUT_OF_SYNC");
        assertThat(stale.body().get("details").get("dartCount").asInt()).isEqualTo(n);

        // Undo a mistaken dart.
        n = dart(5, 1, n);
        Response undone = admin.post("/api/matches/" + matchId + "/undo", Map.of("expectedDartCount", n));
        assertThat(undone.status()).isEqualTo(200);
        n--;
        n = dart(1, 1, n);                                                       // admin 41 -> 40, turn over
        JsonNode state = admin.get("/api/matches/" + matchId).body();
        assertThat(state.get("remaining").get(0).asInt()).isEqualTo(40);
        assertThat(state.get("currentPlayerIndex").asInt()).isEqualTo(1);

        n = dart(1, 1, n); n = dart(1, 1, n); n = dart(1, 1, n);                // alice
        JsonNode beforeCheckout = admin.get("/api/matches/" + matchId).body();
        assertThat(beforeCheckout.get("checkout").get(0).asString()).isEqualTo("D20");

        // Alice cannot score with a wrong dart count either; then admin checks out on D20.
        dart(20, 2, n);
        JsonNode done = admin.get("/api/matches/" + matchId).body();
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("winnerIndex").asInt()).isZero();

        Response afterEnd = admin.post("/api/matches/" + matchId + "/throws", Map.of("segment", 1, "multiplier", 1, "expectedDartCount", n + 1));
        assertThat(afterEnd.code()).isEqualTo("MATCH_NOT_IN_PROGRESS");

        JsonNode stats = alice.get("/api/users/admin/stats").body();
        assertThat(stats.get("matchesWon").asInt()).isEqualTo(1);
        assertThat(stats.get("count180").asInt()).isEqualTo(1);
        assertThat(stats.get("highestCheckout").asInt()).isEqualTo(40);
        assertThat(stats.get("player").get("rating").asInt()).isGreaterThan(1500);
        assertThat(stats.get("ratingHistory").size()).isEqualTo(1);

        JsonNode h2h = admin.get("/api/users/admin/head-to-head/alice").body();
        assertThat(h2h.get("won").asInt()).isEqualTo(1);

        JsonNode board = alice.get("/api/users/leaderboard").body();
        assertThat(board.get(0).get("player").get("username").asString()).isEqualTo("admin");
        probe.close();
    }

    private int dart(int segment, int multiplier, int expected) {
        Response r = admin.post("/api/matches/" + matchId + "/throws",
                Map.of("segment", segment, "multiplier", multiplier, "expectedDartCount", expected));
        assertThat(r.status()).as("dart %s x%s: %s", segment, multiplier, r.body()).isEqualTo(200);
        return expected + 1;
    }

    @Test
    @Order(6)
    void socketRejectsMissingTicketAndClientSends() throws Exception {
        StompProbe bad = StompProbe.connectRaw(port, null);
        assertThat(bad.nextFrame()).startsWith("ERROR");
        bad.close();
    }

    @Test
    @Order(7)
    void upheldDisputeVoidsMatchAndRestoresRatings() {
        Response dispute = alice.post("/api/matches/" + matchId + "/disputes", Map.of("reason", "That last dart was a single 20"));
        assertThat(dispute.status()).isEqualTo(201);
        assertThat(alice.post("/api/matches/" + matchId + "/disputes", Map.of("reason", "again")).code())
                .isEqualTo("DISPUTE_ALREADY_OPEN");
        long id = dispute.body().get("id").asLong();
        Response resolved = admin.post("/api/admin/disputes/" + id + "/resolve", Map.of("outcome", "VOID_MATCH", "note", "Agreed"));
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(admin.get("/api/matches/" + matchId).body().get("status").asString()).isEqualTo("VOIDED");
        JsonNode stats = admin.get("/api/users/alice/stats").body();
        assertThat(stats.get("matchesPlayed").asInt()).isZero();
        assertThat(stats.get("player").get("rating").asInt()).isEqualTo(1500);
    }

    @Test
    @Order(8)
    void roundRobinTournamentCompletesWhenAllFixturesArePlayed() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", "Fredagscup");
        req.put("format", "ROUND_ROBIN");
        req.put("startScore", 301);
        req.put("inRule", "STRAIGHT");
        req.put("outRule", "STRAIGHT");
        req.put("legsToWin", 1);
        req.put("participantIds", List.of(adminId, aliceId));
        Response t = alice.post("/api/tournaments", req);
        assertThat(t.status()).isEqualTo(201);
        String tid = t.body().get("summary").get("id").asString();

        JsonNode started = alice.post("/api/tournaments/" + tid + "/start", null).body();
        assertThat(started.get("fixtures").size()).isEqualTo(1);
        long fixtureId = started.get("fixtures").get(0).get("id").asLong();
        matchId = alice.post("/api/tournaments/" + tid + "/fixtures/" + fixtureId + "/start", null)
                .body().get("matchId").asString();

        JsonNode m = admin.get("/api/matches/" + matchId).body();
        int starter = m.get("currentPlayerIndex").asInt();
        // Whoever starts throws 180 + 121 (straight out) to win.
        int n = 0;
        for (int i = 0; i < 3; i++) n = dart(20, 3, n);
        for (int i = 0; i < 3; i++) n = dart(0, 0, n);
        n = dart(20, 3, n);
        n = dart(20, 3, n);
        dart(1, 1, n);
        JsonNode done = admin.get("/api/tournaments/" + tid).body();
        assertThat(done.get("summary").get("status").asString()).isEqualTo("COMPLETED");
        long winnerId = done.get("summary").get("winner").get("id").asLong();
        assertThat(winnerId).isEqualTo(starter == 0 ? m.get("players").get(0).get("id").asLong()
                : m.get("players").get(1).get("id").asLong());
        assertThat(done.get("standings").get(0).get("points").asInt()).isEqualTo(2);
    }

    @Test
    @Order(9)
    void kudosCommentsLikesAndReplies() {
        String code = admin.post("/api/admin/invites", Map.of("maxUses", 1, "validHours", 1)).body().get("code").asString();
        ApiClient bob = new ApiClient(port);
        assertThat(bob.post("/api/auth/register", Map.of("inviteCode", code, "username", "bob", "realName", "Bob Berg",
                "password", "bob-password-123")).status()).isEqualTo(200);

        String base = "/api/matches/" + matchId;
        JsonNode s = bob.put(base + "/kudos", null).body();
        assertThat(s.get("kudos").get("count").asInt()).isEqualTo(1);
        assertThat(s.get("kudos").get("mine").asBoolean()).isTrue();
        assertThat(alice.put(base + "/kudos", null).code()).isEqualTo("KUDOS_OWN_MATCH");

        s = bob.post(base + "/comments", Map.of("body", "Rått kast!")).body();
        long top = s.get("comments").get(0).get("id").asLong();
        s = alice.post(base + "/comments", Map.of("body", "Takk!", "parentId", top)).body();
        long reply = s.get("comments").get(0).get("replies").get(0).get("id").asLong();
        // Replying to a reply stays in the same thread.
        s = admin.post(base + "/comments", Map.of("body", "Enig", "parentId", reply)).body();
        assertThat(s.get("comments").size()).isEqualTo(1);
        assertThat(s.get("comments").get(0).get("replies").size()).isEqualTo(2);

        s = alice.put("/api/comments/" + top + "/like", null).body();
        assertThat(s.get("comments").get(0).get("likes").asInt()).isEqualTo(1);
        assertThat(bob.patch("/api/comments/" + reply, Map.of("body", "hacked")).status()).isEqualTo(403);

        // Deleting a comment that has replies keeps the thread but hides the text.
        s = bob.delete("/api/comments/" + top).body();
        JsonNode first = s.get("comments").get(0);
        assertThat(first.get("deleted").asBoolean()).isTrue();
        assertThat(first.get("body").asString()).isEmpty();
        assertThat(first.get("replies").size()).isEqualTo(2);

        JsonNode recent = admin.get("/api/matches/recent?limit=5").body();
        JsonNode row = null;
        for (JsonNode r : recent) if (r.get("id").asString().equals(matchId)) row = r;
        assertThat(row).isNotNull();
        assertThat(row.get("kudos").asInt()).isEqualTo(1);
        assertThat(row.get("comments").asInt()).isEqualTo(2);
    }

    @Test
    @Order(10)
    void breakdownAndDartStats() {
        JsonNode b = admin.get("/api/matches/" + matchId + "/breakdown").body();
        assertThat(b.get("legs").size()).isEqualTo(1);
        assertThat(b.get("legs").get(0).get("turns").size()).isGreaterThanOrEqualTo(3);
        assertThat(b.get("players").get(0).get("heatmap").size()).isGreaterThan(0);
        double p = b.get("winProbability").get(0).asDouble() + b.get("winProbability").get(1).asDouble();
        assertThat(p).isBetween(99.9, 100.1);

        JsonNode d = alice.get("/api/users/admin/dart-stats").body();
        assertThat(d.get("dartsThrown").asInt()).isGreaterThan(0);
        assertThat(d.get("bands").size()).isEqualTo(8);
        assertThat(d.get("hold").get("of").asInt() + d.get("breaks").get("of").asInt()).isGreaterThan(0);
    }

    @Test
    @Order(11)
    void adminPasswordResetLink() {
        // Lock alice out with wrong passwords.
        ApiClient attacker = new ApiClient(port);
        Response last = null;
        for (int i = 0; i < 7; i++) last = attacker.login("alice", "wrong-password-" + i);
        assertThat(last.code()).isEqualTo("RATE_LIMITED");

        assertThat(alice.post("/api/admin/users/" + aliceId + "/password-reset", null).status()).isEqualTo(403);
        Response link = admin.post("/api/admin/users/" + aliceId + "/password-reset", null);
        assertThat(link.status()).isEqualTo(201);
        String token = link.body().get("token").asString();

        ApiClient phone = new ApiClient(port);
        JsonNode check = phone.post("/api/auth/password-reset/check", Map.of("token", token)).body();
        assertThat(check.get("valid").asBoolean()).isTrue();
        assertThat(check.get("username").asString()).isEqualTo("alice");

        Response reset = phone.post("/api/auth/password-reset", Map.of("token", token, "newPassword", "brand-new-pass-42"));
        assertThat(reset.status()).isEqualTo(200);
        assertThat(phone.get("/api/auth/me").body().get("username").asString()).isEqualTo("alice");

        // Every other session is gone, the lock-out is lifted, and the link only works once.
        assertThat(alice.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(new ApiClient(port).login("alice", "brand-new-pass-42").status()).isEqualTo(200);
        assertThat(phone.post("/api/auth/password-reset", Map.of("token", token, "newPassword", "another-pass-99")).code())
                .isEqualTo("RESET_LINK_INVALID");
        assertThat(phone.post("/api/auth/password-reset/check", Map.of("token", token)).body().get("valid").asBoolean()).isFalse();
    }

    @org.springframework.beans.factory.annotation.Autowired
    no.trafondart.awards.AwardService awardService;

    @Test
    @Order(12)
    void playerOfTheMonthRaceAndBadges() {
        JsonNode race = admin.get("/api/awards/current").body();
        assertThat(race.get("period").asString()).matches("\\d{4}-\\d{2}");
        assertThat(race.get("standings").size()).isGreaterThanOrEqualTo(2);
        JsonNode leader = race.get("standings").get(0);
        assertThat(leader.get("eligible").asBoolean()).isTrue();
        assertThat(leader.get("points").get("total").asDouble()).isGreaterThan(0);
        boolean hasPotm = false;
        for (JsonNode l : race.get("leaders")) hasPotm |= l.get("award").asString().equals("PLAYER_OF_MONTH");
        assertThat(hasPotm).isTrue();

        // Decide this month as if it had ended, and the winner gets a badge.
        java.time.YearMonth now = awardService.currentMonth();
        awardService.finalizeMonth(now);
        String winner = leader.get("player").get("username").asString();
        JsonNode badges = admin.get("/api/users/" + winner + "/awards").body();
        boolean potm = false;
        for (JsonNode b : badges) potm |= b.get("award").asString().equals("PLAYER_OF_MONTH");
        assertThat(potm).isTrue();
        JsonNode hall = admin.get("/api/awards").body();
        assertThat(hall.get(0).get("period").asString()).isEqualTo(now.toString());

        ApiClient member = new ApiClient(port);
        member.login("alice", "brand-new-pass-42"); // alice's old session was revoked by the reset test
        assertThat(member.post("/api/admin/awards/recompute", null).status()).isEqualTo(403);
        awardService.invalidateFrom(now);
        assertThat(admin.get("/api/users/" + winner + "/awards").body().size()).isZero();
    }

    // ------------------------------------------------------------------ minimal STOMP client

    static final class StompProbe implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket ws;

        static StompProbe connect(int port, String ticket) throws Exception {
            StompProbe p = connectRaw(port, ticket);
            assertThat(p.nextFrame()).startsWith("CONNECTED");
            return p;
        }

        static StompProbe connectRaw(int port, String ticket) throws Exception {
            StompProbe p = new StompProbe();
            p.ws = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws"), p).get(5, TimeUnit.SECONDS);
            String headers = "accept-version:1.2\nhost:localhost\nheart-beat:0,0\n" + (ticket == null ? "" : "ticket:" + ticket + "\n");
            p.ws.sendText("CONNECT\n" + headers + "\n\u0000", true).get(5, TimeUnit.SECONDS);
            return p;
        }

        void subscribe(String destination) throws Exception {
            ws.sendText("SUBSCRIBE\nid:sub-0\ndestination:" + destination + "\n\n\u0000", true).get(5, TimeUnit.SECONDS);
            Thread.sleep(200);
        }

        String nextFrame() throws InterruptedException {
            String f = frames.poll(10, TimeUnit.SECONDS);
            assertThat(f).as("expected a STOMP frame").isNotNull();
            return f;
        }

        JsonNode nextMessage() throws InterruptedException {
            String f;
            do {
                f = nextFrame();
            } while (!f.startsWith("MESSAGE"));
            String body = f.substring(f.indexOf("\n\n") + 2).replace("\u0000", "");
            return tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
        }

        void close() {
            ws.abort();
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                if (!text.isBlank()) frames.add(text);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
