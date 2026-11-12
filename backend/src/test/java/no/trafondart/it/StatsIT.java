package no.trafondart.it;

import no.trafondart.awards.AwardService;
import no.trafondart.it.ApiClient.Raw;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Statistics for edge cases: nobody has played yet, month boundaries, tournament locks. */
class StatsIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AwardService awards;

    /** NaN or Infinity would make the JSON unparseable (or show "NaN" on screen). */
    private static void assertFiniteNumbers(String json) {
        assertThat(json).doesNotContain("NaN", "Infinity");
    }

    @Test
    void aBrandNewMemberHasCleanEmptyStatistics() {
        Member m = member("fresh");
        for (String path : new String[]{"/api/users/" + m.username() + "/stats", "/api/users/" + m.username() + "/dart-stats",
                "/api/users/" + m.username() + "/head-to-head", "/api/users/" + m.username() + "/awards",
                "/api/users/leaderboard", "/api/matches/recent?user=" + m.username()}) {
            Raw r = m.client().raw("GET", path, null, null);
            assertThat(r.status()).as(path).isEqualTo(200);
            assertFiniteNumbers(r.body());
        }
        JsonNode s = m.client().get("/api/users/" + m.username() + "/stats").body();
        assertThat(s.get("matchesPlayed").asInt()).isZero();
        assertThat(s.get("average").asDouble()).isZero();
        assertThat(s.get("player").get("provisional").asBoolean()).isTrue();
    }

    @Test
    void aMatchWithNoDartsYetHasACleanBreakdown() {
        Member a = member("emptyA"), b = member("emptyB");
        String id = quickMatch(a.client(), a.id(), b.id(), false);
        Raw r = a.client().raw("GET", "/api/matches/" + id + "/breakdown", null, null);
        assertThat(r.status()).isEqualTo(200);
        assertFiniteNumbers(r.body());
        assertFiniteNumbers(a.client().raw("GET", "/api/matches/" + id, null, null).body());
    }

    @Test
    void theLeaderboardIsSortedByRatingAndHidesDisabledMembers() {
        Member a = member("lbA"), b = member("lbB");
        finishedMatch(a.client(), a.id(), b.id(), true, 0);
        JsonNode board = a.client().get("/api/users/leaderboard").body();
        int previous = Integer.MAX_VALUE;
        int rank = 0;
        for (JsonNode row : board) {
            int rating = row.get("player").get("rating").asInt();
            assertThat(rating).isLessThanOrEqualTo(previous);
            assertThat(row.get("rank").asInt()).isGreaterThan(rank);
            previous = rating;
            rank = row.get("rank").asInt();
        }
        admin.patch("/api/admin/users/" + b.id(), Map.of("enabled", false));
        assertThat(a.client().get("/api/users/leaderboard").body().toString()).doesNotContain("\"" + b.username() + "\"");
        admin.patch("/api/admin/users/" + b.id(), Map.of("enabled", true));
    }

    @Test
    void monthsFollowNorwegianTimeNotUtc() {
        Member a = member("tzA"), b = member("tzB");
        String id = finishedMatch(a.client(), a.id(), b.id(), true, 0).get("id").asString();
        // 23:30 UTC on 31 January is 00:30 on 1 February in Oslo.
        jdbc.update("update matches set completed_at = ? where id = ?", Timestamp.from(Instant.parse("2026-01-31T23:30:00Z")), UUID.fromString(id));
        assertThat(awards.standings(YearMonth.of(2026, 2))).anyMatch(s -> s.player().username().equals(a.username()));
        assertThat(awards.standings(YearMonth.of(2026, 1))).noneMatch(s -> s.player().username().equals(a.username()));
        // 22:59 UTC on 31 January is still January in Oslo
        jdbc.update("update matches set completed_at = ? where id = ?", Timestamp.from(Instant.parse("2026-01-31T22:59:00Z")), UUID.fromString(id));
        assertThat(awards.standings(YearMonth.of(2026, 1))).anyMatch(s -> s.player().username().equals(a.username()));
        // summer time: 22:30 UTC on 31 July is 00:30 on 1 August
        jdbc.update("update matches set completed_at = ? where id = ?", Timestamp.from(Instant.parse("2026-07-31T22:30:00Z")), UUID.fromString(id));
        assertThat(awards.standings(YearMonth.of(2026, 8))).anyMatch(s -> s.player().username().equals(a.username()));
        jdbc.update("update matches set completed_at = now() where id = ?", UUID.fromString(id));
    }

    @Test
    void aCupResultCannotBeVoidedOnceTheWinnerHasPlayedOn() {
        Member p1 = member("lockA"), p2 = member("lockB"), p3 = member("lockC"), p4 = member("lockD");
        var created = admin.post("/api/tournaments", Map.of("name", unique("Lock"), "format", "SINGLE_ELIMINATION",
                "startScore", 301, "inRule", "STRAIGHT", "outRule", "STRAIGHT", "legsToWin", 1, "rated", true,
                "participantIds", java.util.List.of(p1.id(), p2.id(), p3.id(), p4.id())));
        String tid = created.body().get("summary").get("id").asString();
        admin.post("/api/tournaments/" + tid + "/start", null);
        JsonNode t = admin.get("/api/tournaments/" + tid).body();
        String semi1 = null;
        for (JsonNode f : t.get("fixtures")) {
            if (f.get("round").asInt() != 1) continue;
            String mid = admin.post("/api/tournaments/" + tid + "/fixtures/" + f.get("id").asLong() + "/start", null).body().get("matchId").asString();
            playToEnd(admin, mid, 0);
            if (semi1 == null) semi1 = mid;
        }
        JsonNode fin = null;
        for (JsonNode f : admin.get("/api/tournaments/" + tid).body().get("fixtures")) if (f.get("round").asInt() == 2) fin = f;
        assertThat(fin.get("status").asString()).isEqualTo("READY");
        admin.post("/api/tournaments/" + tid + "/fixtures/" + fin.get("id").asLong() + "/start", null);

        // A semi-final loser disputes; the winner has already started the final.
        long semiLoser = admin.get("/api/matches/" + semi1).body().get("players").get(1).get("id").asLong();
        Member loser = java.util.stream.Stream.of(p1, p2, p3, p4).filter(m -> m.id() == semiLoser).findFirst().orElseThrow();
        long did = loser.client().post("/api/matches/" + semi1 + "/disputes", Map.of("reason", "wrong double")).body().get("id").asLong();
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "VOID_MATCH")).code()).isEqualTo("DISPUTE_TOURNAMENT_LOCKED");
        // nothing changed: the match stands and the dispute is still open for another decision
        assertThat(admin.get("/api/matches/" + semi1).body().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "REJECT")).status()).isEqualTo(200);
    }
}
