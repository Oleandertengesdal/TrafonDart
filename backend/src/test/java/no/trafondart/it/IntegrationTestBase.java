package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared Spring context for the end-to-end API tests: a real server on a random port and a real PostgreSQL
 * database, wiped and migrated once when the context starts. Test classes never assume an empty database –
 * each one creates its own members with unique usernames.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(IntegrationTestBase.CleanDatabase.class)
abstract class IntegrationTestBase {

    static final String ADMIN_PASSWORD = "test-admin-password";
    static final String PASSWORD = "member-password-1";
    private static final AtomicInteger SEQ = new AtomicInteger();

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
    long adminId;

    record Member(ApiClient client, long id, String username, String password) {
    }

    @BeforeAll
    void loginAdmin() {
        admin = client();
        Response r = admin.login("admin", ADMIN_PASSWORD);
        assertThat(r.status()).as("admin login: %s", r.body()).isEqualTo(200);
        adminId = r.body().get("id").asLong();
    }

    ApiClient client() {
        return new ApiClient(port);
    }

    static String unique(String prefix) {
        return prefix + "_" + Integer.toString(SEQ.incrementAndGet(), 36) + Long.toString(System.nanoTime() % 1296, 36);
    }

    String invite(int uses) {
        Response r = admin.post("/api/admin/invites", Map.of("maxUses", uses, "validHours", 24));
        assertThat(r.status()).isEqualTo(201);
        return r.body().get("code").asString();
    }

    Map<String, Object> registration(String code, String username, String password) {
        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("inviteCode", code);
        reg.put("username", username);
        reg.put("realName", "Test " + username.replaceAll("[^A-Za-z]", ""));
        reg.put("password", password);
        reg.put("preferredLocale", "nb");
        return reg;
    }

    /** Registers a new member through a fresh invite and returns their logged-in client. */
    Member member(String prefix) {
        String username = unique(prefix);
        ApiClient c = client();
        Response r = c.post("/api/auth/register", registration(invite(1), username, PASSWORD));
        assertThat(r.status()).as("register %s: %s", username, r.body()).isEqualTo(200);
        return new Member(c, r.body().get("id").asLong(), username, PASSWORD);
    }

    static Map<String, Object> matchBody(long p1, long p2, boolean rated, int startScore, String outRule, int legs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("player1Id", p1);
        m.put("player2Id", p2);
        m.put("startScore", startScore);
        m.put("inRule", "STRAIGHT");
        m.put("outRule", outRule);
        m.put("legsToWin", legs);
        m.put("startingPlayer", 0);
        m.put("rated", rated);
        return m;
    }

    /** A quick 301 straight-out, one-leg match between two members, created by {@code creator}. */
    String quickMatch(ApiClient creator, long p1, long p2, boolean rated) {
        Response r = creator.post("/api/matches", matchBody(p1, p2, rated, 301, "STRAIGHT", 1));
        assertThat(r.status()).as("create match: %s", r.body()).isEqualTo(201);
        return r.body().get("id").asString();
    }

    Response throwDart(ApiClient scorer, String matchId, int segment, int multiplier, int expected) {
        return scorer.post("/api/matches/" + matchId + "/throws",
                Map.of("segment", segment, "multiplier", multiplier, "expectedDartCount", expected));
    }

    /**
     * Plays the match to the end: the player at {@code winnerIndex} scores efficiently, the other misses.
     * Works for any out-rule. Returns the final match state.
     */
    JsonNode playToEnd(ApiClient scorer, String matchId, int winnerIndex) {
        JsonNode s = scorer.get("/api/matches/" + matchId).body();
        int guard = 0;
        while ("IN_PROGRESS".equals(s.get("status").asString())) {
            assertThat(guard++).as("match should end").isLessThan(500);
            int current = s.get("currentPlayerIndex").asInt();
            int left = s.get("remaining").get(current).asInt();
            int[] dart = current == winnerIndex ? bestDart(s, left) : new int[]{0, 0};
            Response r = throwDart(scorer, matchId, dart[0], dart[1], s.get("dartCount").asInt());
            assertThat(r.status()).as("dart %s: %s", java.util.Arrays.toString(dart), r.body()).isEqualTo(200);
            s = r.body();
        }
        return s;
    }

    private static int[] bestDart(JsonNode s, int left) {
        JsonNode route = s.get("checkout");
        if (route != null && route.isArray() && !route.isEmpty()) return parse(route.get(0).asString());
        if (left > 60) return new int[]{20, 3};
        // straight out without a suggested route: get closer with a single
        return new int[]{Math.min(20, Math.max(1, left - 2)), 1};
    }

    private static int[] parse(String label) {
        return switch (label) {
            case "BULL" -> new int[]{25, 2};
            case "25" -> new int[]{25, 1};
            case "MISS" -> new int[]{0, 0};
            default -> new int[]{Integer.parseInt(label.substring(1)),
                    label.charAt(0) == 'T' ? 3 : label.charAt(0) == 'D' ? 2 : 1};
        };
    }

    /** Creates and finishes a match in one go. */
    JsonNode finishedMatch(ApiClient creator, long p1, long p2, boolean rated, int winnerIndex) {
        String id = quickMatch(creator, p1, p2, rated);
        return playToEnd(creator, id, winnerIndex);
    }

    /** No error body may ever leak implementation details. */
    static void assertNoLeak(String body) {
        if (body == null) return;
        assertThat(body).doesNotContain("Exception", "at no.", "at org.", "org.postgresql", "SQL", "hibernate",
                "jakarta.", "java.lang", "stackTrace", "trace\"");
    }
}
