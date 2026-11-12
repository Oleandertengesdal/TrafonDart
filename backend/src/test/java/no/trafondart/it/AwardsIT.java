package no.trafondart.it;

import no.trafondart.awards.AwardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Player of the Month can't be gamed, and corrections flow through to it. */
class AwardsIT extends IntegrationTestBase {

    @Autowired
    AwardService awards;

    private JsonNode standing(String username) {
        for (JsonNode s : admin.get("/api/awards/current").body().get("standings")) {
            if (s.get("player").get("username").asString().equals(username)) return s;
        }
        return null;
    }

    @Test
    void casualMatchesScoredBySomeoneElseCannotWinAnybodyAnAward() {
        Member a = member("awA"), b = member("awB"), stranger = member("awS");
        // The stranger invents a casual match where `a` throws nothing but 180s.
        String id = quickMatch(stranger.client(), a.id(), b.id(), false);
        int n = 0;
        for (int i = 0; i < 2; i++) {
            for (int d = 0; d < 3; d++) throwDart(stranger.client(), id, 20, 3, n++);
            for (int d = 0; d < 3; d++) throwDart(stranger.client(), id, 0, 0, n++);
        }
        playToEnd(stranger.client(), id, 0);
        JsonNode s = standing(a.username());
        if (s != null) {
            assertThat(s.get("stats").get("count180").asInt()).as("180s from a match a didn't sign up for").isZero();
            assertThat(s.get("stats").get("matches").asInt()).isZero();
        }
    }

    @Test
    void ratedMatchesCountAndAVoidTakesThemOutAgain() {
        Member a = member("awvA"), b = member("awvB");
        String id = finishedMatch(a.client(), a.id(), b.id(), true, 0).get("id").asString();
        JsonNode s = standing(a.username());
        assertThat(s).isNotNull();
        assertThat(s.get("stats").get("matches").asInt()).isEqualTo(1);
        assertThat(s.get("stats").get("wins").asInt()).isEqualTo(1);
        assertThat(s.get("points").get("total").asDouble()).isPositive();

        long did = b.client().post("/api/matches/" + id + "/disputes", Map.of("reason", "practice board")).body().get("id").asLong();
        admin.post("/api/admin/disputes/" + did + "/resolve", Map.of("outcome", "VOID_MATCH"));
        assertThat(standing(a.username())).isNull();
    }

    @Test
    void disabledMembersAreNotInTheRace() {
        Member a = member("awdA"), b = member("awdB");
        finishedMatch(a.client(), a.id(), b.id(), true, 0);
        assertThat(standing(a.username())).isNotNull();
        admin.patch("/api/admin/users/" + a.id(), Map.of("enabled", false));
        assertThat(standing(a.username())).isNull();
        admin.patch("/api/admin/users/" + a.id(), Map.of("enabled", true));
    }

    @Test
    void endpointsValidateInputAndProtectRecompute() {
        Member m = member("awEp");
        assertThat(m.client().get("/api/users/nobody_here/awards").status()).isEqualTo(404);
        assertThat(m.client().get("/api/awards?limit=0").status()).isEqualTo(200);
        assertThat(m.client().get("/api/awards?limit=-1").status()).isEqualTo(200);
        assertThat(m.client().get("/api/awards?limit=abc").status()).isEqualTo(400);
        assertThat(m.client().post("/api/admin/awards/recompute", null).status()).isEqualTo(403);
        assertThat(admin.post("/api/admin/awards/recompute", null).status()).isEqualTo(200);
        JsonNode race = m.client().get("/api/awards/current").body();
        assertThat(race.get("minMatches").asInt()).isPositive();
        assertThat(race.get("endsAt").asString()).isGreaterThan(race.get("startsAt").asString());
    }

    @Test
    void finalisingIsIdempotent() {
        java.time.YearMonth past = awards.currentMonth().minusMonths(1);
        awards.finalizeMonth(past);
        int before = admin.get("/api/awards?limit=120").body().size();
        awards.finalizeMonth(past);
        awards.ensureFinalized();
        assertThat(admin.get("/api/awards?limit=120").body().size()).isEqualTo(before);
    }
}
