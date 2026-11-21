package no.trafondart.it;

import no.trafondart.awards.AwardService;
import no.trafondart.it.ApiClient.Response;
import no.trafondart.match.MatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A finished normal match counts for nothing until it is confirmed: by the player who didn't register it, by an
 * admin, or by itself after two days. A disputed one waits for an admin. Tournament matches need no confirmation.
 */
class MatchConfirmationIT extends IntegrationTestBase {

    @Autowired
    MatchService matchService;

    @Autowired
    AwardService awards;

    @Autowired
    JdbcTemplate jdbc;

    private int rating(Member m) {
        return m.client().get("/api/users/" + m.username() + "/stats").body().get("player").get("rating").asInt();
    }

    private int played(Member m) {
        return m.client().get("/api/users/" + m.username() + "/stats").body().get("matchesPlayed").asInt();
    }

    /** A rated match that {@code creator} sets up and scores to the end: player 1 wins. Not confirmed. */
    private String finishedUnconfirmed(Member creator, long p1, long p2, boolean rated) {
        String id = quickMatch(creator.client(), p1, p2, rated);
        JsonNode end = playToEnd(creator.client(), id, 0);
        assertThat(end.get("status").asString()).isEqualTo("UNCONFIRMED");
        return id;
    }

    private void backdate(String id, Duration ago) {
        jdbc.update("update matches set completed_at = ? where id = ?", Timestamp.from(Instant.now().minus(ago)), UUID.fromString(id));
    }

    private int leaderboardPlayed(Member m) {
        for (JsonNode row : m.client().get("/api/users/leaderboard").body()) {
            if (row.get("player").get("username").asString().equals(m.username())) return row.get("played").asInt();
        }
        throw new AssertionError("not on the leaderboard: " + m.username());
    }

    private String status(String id) {
        return admin.get("/api/matches/" + id).body().get("status").asString();
    }

    @Test
    void aFinishedMatchCountsForNothingUntilTheOtherPlayerConfirms() {
        Member a = member("cfA"), b = member("cfB"), c = member("cfC");
        String id = finishedUnconfirmed(a, a.id(), b.id(), true);

        JsonNode view = a.client().get("/api/matches/" + id).body();
        assertThat(view.get("winnerIndex").asInt()).isZero();
        assertThat(view.get("completedAt").isNull()).isFalse();
        JsonNode conf = view.get("confirmation");
        assertThat(conf.get("registeredBy").asString()).isEqualTo(a.username());
        assertThat(conf.get("confirmerIds")).hasSize(1);
        assertThat(conf.get("confirmerIds").get(0).asLong()).isEqualTo(b.id());
        assertThat(conf.get("confirmedAt").isNull()).isTrue();
        assertThat(Instant.parse(conf.get("autoConfirmAt").asString()))
                .isEqualTo(Instant.parse(view.get("completedAt").asString()).plus(Duration.ofDays(2)));

        // nothing counts yet: rating, stats, form, leaderboard, head-to-head
        assertThat(rating(a)).isEqualTo(1500);
        assertThat(rating(b)).isEqualTo(1500);
        assertThat(played(a)).isZero();
        assertThat(a.client().get("/api/users/" + a.username() + "/head-to-head").body()).isEmpty();
        assertThat(leaderboardPlayed(a)).isZero();
        // but it is in the history, marked as waiting
        JsonNode recent = a.client().get("/api/matches/recent?user=" + a.username()).body();
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).get("status").asString()).isEqualTo("UNCONFIRMED");
        // and on B's list of results to confirm, not on A's
        assertThat(b.client().get("/api/matches/to-confirm").body().toString()).contains(id);
        assertThat(a.client().get("/api/matches/to-confirm").body().toString()).doesNotContain(id);

        // the player who registered it can't confirm it, nor can an outsider
        Response own = a.client().post("/api/matches/" + id + "/confirm", null);
        assertThat(own.status()).isEqualTo(403);
        assertThat(own.code()).isEqualTo("MATCH_CONFIRM_NOT_ALLOWED");
        assertThat(c.client().post("/api/matches/" + id + "/confirm", null).status()).isEqualTo(403);
        assertThat(status(id)).isEqualTo("UNCONFIRMED");

        // B confirms: now it counts everywhere
        Response ok = b.client().post("/api/matches/" + id + "/confirm", null);
        assertThat(ok.status()).as(ok.body().toString()).isEqualTo(200);
        assertThat(ok.body().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(ok.body().get("confirmation").get("confirmedBy").asString()).isEqualTo(b.username());
        assertThat(ok.body().get("confirmation").get("confirmedAt").isNull()).isFalse();
        assertThat(rating(a)).isGreaterThan(1500);
        assertThat(rating(b)).isLessThan(1500);
        assertThat(played(a)).isEqualTo(1);
        assertThat(leaderboardPlayed(a)).isEqualTo(1);
        assertThat(a.client().get("/api/users/" + a.username() + "/head-to-head").body()).hasSize(1);
        assertThat(b.client().get("/api/matches/to-confirm").body().toString()).doesNotContain(id);

        // once is enough
        Response again = b.client().post("/api/matches/" + id + "/confirm", null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("MATCH_NOT_UNCONFIRMED");
    }

    @Test
    void anAdminCanAlwaysConfirm() {
        Member a = member("cfadA"), b = member("cfadB");
        String id = finishedUnconfirmed(a, a.id(), b.id(), true);
        Response r = admin.post("/api/matches/" + id + "/confirm", null);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(200);
        assertThat(r.body().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(rating(a)).isGreaterThan(1500);
    }

    @Test
    void whenSomeoneElseScoredItEitherPlayerConfirms() {
        Member a = member("cf3A"), b = member("cf3B"), scorer = member("cf3S");
        String id = finishedUnconfirmed(scorer, a.id(), b.id(), false);
        JsonNode conf = admin.get("/api/matches/" + id).body().get("confirmation");
        assertThat(conf.get("confirmerIds")).hasSize(2);
        assertThat(scorer.client().post("/api/matches/" + id + "/confirm", null).status()).isEqualTo(403);
        assertThat(a.client().post("/api/matches/" + id + "/confirm", null).status()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("COMPLETED");
    }

    @Test
    void aDisputedMatchWaitsForAnAdminWhoConfirmsOrVoidsIt() {
        Member a = member("cfdA"), b = member("cfdB");

        // disputed, then the admin rejects the dispute: the match is confirmed
        String kept = finishedUnconfirmed(a, a.id(), b.id(), true);
        Response d = b.client().post("/api/matches/" + kept + "/disputes", Map.of("reason", "I won the last leg"));
        assertThat(d.status()).as(d.body().toString()).isEqualTo(201);
        JsonNode view = a.client().get("/api/matches/" + kept).body();
        assertThat(view.get("disputed").asBoolean()).isTrue();
        assertThat(view.get("status").asString()).isEqualTo("UNCONFIRMED");
        // a disputed match can't be confirmed by the player any more, and drops off their list
        Response blocked = b.client().post("/api/matches/" + kept + "/confirm", null);
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.code()).isEqualTo("DISPUTE_ALREADY_OPEN");
        assertThat(b.client().get("/api/matches/to-confirm").body().toString()).doesNotContain(kept);
        Response rejected = admin.post("/api/admin/disputes/" + d.body().get("id").asLong() + "/resolve", Map.of("outcome", "REJECT"));
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(status(kept)).isEqualTo("COMPLETED");
        int afterFirst = rating(a);
        assertThat(afterFirst).isGreaterThan(1500);

        // disputed, then voided: it never counted, so nothing changes
        String voided = finishedUnconfirmed(a, a.id(), b.id(), true);
        long did = b.client().post("/api/matches/" + voided + "/disputes", Map.of("reason", "never played")).body().get("id").asLong();
        assertThat(admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "VOID_MATCH")).status()).isEqualTo(200);
        assertThat(status(voided)).isEqualTo("VOIDED");
        assertThat(rating(a)).isEqualTo(afterFirst);
        assertThat(played(a)).isEqualTo(1);

        // an admin confirming a disputed match directly also closes the dispute
        String direct = finishedUnconfirmed(a, a.id(), b.id(), true);
        b.client().post("/api/matches/" + direct + "/disputes", Map.of("reason", "not sure"));
        assertThat(admin.post("/api/matches/" + direct + "/confirm", null).status()).isEqualTo(200);
        assertThat(status(direct)).isEqualTo("COMPLETED");
        assertThat(admin.get("/api/matches/" + direct).body().get("disputed").asBoolean()).isFalse();
    }

    @Test
    void anUnansweredMatchConfirmsItselfAfterTwoDaysUnlessItIsDisputed() {
        Member a = member("cfauA"), b = member("cfauB");
        String due = finishedUnconfirmed(a, a.id(), b.id(), true);
        String notYet = finishedUnconfirmed(a, a.id(), b.id(), true);
        String disputed = finishedUnconfirmed(a, a.id(), b.id(), true);
        backdate(due, Duration.ofHours(49));
        backdate(notYet, Duration.ofHours(47));
        backdate(disputed, Duration.ofDays(3));
        b.client().post("/api/matches/" + disputed + "/disputes", Map.of("reason", "wrong"));

        assertThat(matchService.autoConfirmDue()).isGreaterThanOrEqualTo(1);

        assertThat(status(due)).isEqualTo("COMPLETED");
        JsonNode conf = admin.get("/api/matches/" + due).body().get("confirmation");
        assertThat(conf.get("confirmedBy").isNull()).as("confirmed by nobody: automatically").isTrue();
        assertThat(conf.get("confirmedAt").isNull()).isFalse();
        assertThat(status(notYet)).isEqualTo("UNCONFIRMED");
        assertThat(status(disputed)).isEqualTo("UNCONFIRMED");
        assertThat(played(a)).isEqualTo(1);
        assertThat(rating(a)).isGreaterThan(1500);
    }

    @Test
    void ratingsRebuiltFromScratchMatchTheConfirmationOrder() {
        Member a = member("cfoA"), b = member("cfoB"), c = member("cfoC");
        // played first, confirmed last
        String first = finishedUnconfirmed(a, a.id(), b.id(), true);
        String second = finishedUnconfirmed(c, c.id(), a.id(), true);
        assertThat(a.client().post("/api/matches/" + second + "/confirm", null).status()).isEqualTo(200);
        assertThat(b.client().post("/api/matches/" + first + "/confirm", null).status()).isEqualTo(200);
        int ra = rating(a), rb = rating(b), rc = rating(c);
        assertThat(admin.post("/api/admin/ratings/recompute", null).status()).isEqualTo(200);
        assertThat(rating(a)).isEqualTo(ra);
        assertThat(rating(b)).isEqualTo(rb);
        assertThat(rating(c)).isEqualTo(rc);
    }

    @Test
    void confirmingAMatchFromAFinishedMonthDecidesThatMonthAgain() {
        Member a = member("cfmA"), b = member("cfmB");
        String id = finishedUnconfirmed(a, a.id(), b.id(), true);
        YearMonth last = awards.currentMonth().minusMonths(1);
        Instant mid = last.atDay(15).atTime(20, 0).atZone(ZoneId.of("Europe/Oslo")).toInstant();
        jdbc.update("update matches set completed_at = ? where id = ?", Timestamp.from(mid), UUID.fromString(id));
        // months are decided from the first counted match on: make sure last month has one
        Member c = member("cfmC"), d = member("cfmD");
        String counted = finishedMatch(c.client(), c.id(), d.id(), true, 0).get("id").asString();
        Instant early = last.atDay(10).atTime(20, 0).atZone(ZoneId.of("Europe/Oslo")).toInstant();
        jdbc.update("update matches set completed_at = ?, confirmed_at = ? where id = ?", Timestamp.from(early), Timestamp.from(early),
                UUID.fromString(counted));
        awards.ensureFinalized();
        assertThat(jdbc.queryForObject("select count(*) from award_periods where period = ?", Integer.class, last.toString())).isEqualTo(1);
        assertThat(awards.standings(last)).noneMatch(s -> s.player().username().equals(a.username()));

        assertThat(b.client().post("/api/matches/" + id + "/confirm", null).status()).isEqualTo(200);

        // the month is decided again (lazily, on the next request) and now includes the match
        assertThat(jdbc.queryForObject("select count(*) from award_periods where period = ?", Integer.class, last.toString())).isZero();
        assertThat(awards.standings(last)).anyMatch(s -> s.player().username().equals(a.username()));
        awards.ensureFinalized();
        assertThat(jdbc.queryForObject("select count(*) from award_periods where period = ?", Integer.class, last.toString())).isEqualTo(1);
    }

    @Test
    void tournamentMatchesCountAsSoonAsTheyAreWon() {
        Member p1 = member("cftA"), p2 = member("cftB");
        Response created = admin.post("/api/tournaments", Map.of("name", unique("Conf"), "format", "SINGLE_ELIMINATION",
                "startScore", 301, "inRule", "STRAIGHT", "outRule", "STRAIGHT", "legsToWin", 1, "rated", true,
                "participantIds", java.util.List.of(p1.id(), p2.id())));
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        String tid = created.body().get("summary").get("id").asString();
        assertThat(admin.post("/api/tournaments/" + tid + "/start", null).status()).isEqualTo(200);
        JsonNode fixture = null;
        for (JsonNode f : admin.get("/api/tournaments/" + tid).body().get("fixtures")) if (f.get("status").asString().equals("READY")) fixture = f;
        assertThat(fixture).isNotNull();
        Response started = admin.post("/api/tournaments/" + tid + "/fixtures/" + fixture.get("id").asLong() + "/start", null);
        assertThat(started.status()).as(started.body().toString()).isEqualTo(200);
        String matchId = started.body().get("matchId").asString();
        JsonNode end = playToEnd(admin, matchId, 0);
        assertThat(end.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(end.get("confirmation").isNull()).as("nothing to confirm").isTrue();
        assertThat(admin.get("/api/tournaments/" + tid).body().get("summary").get("status").asString()).isEqualTo("COMPLETED");
    }
}
