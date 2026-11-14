package no.trafondart.it;

import no.trafondart.it.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 401 can be played wherever X01 can: matches, party X01, tournaments and challenges. Other scores still can't. */
class FourOhOneIT extends IntegrationTestBase {

    @Test
    void aMatchOf401IsPlayedToTheEnd() {
        Member a = member("f01A"), b = member("f01B");
        Response r = a.client().post("/api/matches", matchBody(a.id(), b.id(), true, 401, "DOUBLE", 1));
        assertThat(r.status()).as("%s", r.body()).isEqualTo(201);
        assertThat(r.body().get("remaining").get(0).asInt()).isEqualTo(401);
        JsonNode done = playToEnd(a.client(), r.body().get("id").asString(), 0);
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(a.client().post("/api/matches", matchBody(a.id(), b.id(), true, 402, "DOUBLE", 1)).status()).isEqualTo(400);
    }

    @Test
    void partyX01TournamentsAndChallengesTake401() {
        Member a = member("f01C"), b = member("f01D"), c = member("f01E");

        Map<String, Object> game = new HashMap<>();
        game.put("type", "X01");
        game.put("playerIds", List.of(a.id(), b.id(), c.id()));
        game.put("teams", false);
        game.put("settings", Map.of("startScore", 401, "inRule", "STRAIGHT", "outRule", "DOUBLE", "legsToWin", 1));
        Response g = a.client().post("/api/games", game);
        assertThat(g.status()).as("%s", g.body()).isEqualTo(201);
        a.client().post("/api/games/" + g.body().get("id").asString() + "/abandon", null);

        Map<String, Object> t = new HashMap<>();
        t.put("name", unique("Cup401"));
        t.put("format", "ROUND_ROBIN");
        t.put("startScore", 401);
        t.put("inRule", "STRAIGHT");
        t.put("outRule", "DOUBLE");
        t.put("legsToWin", 1);
        t.put("rated", false);
        t.put("participantIds", List.of(a.id(), b.id(), c.id()));
        Response tour = a.client().post("/api/tournaments", t);
        assertThat(tour.status()).as("%s", tour.body()).isEqualTo(201);
        assertThat(tour.body().get("summary").get("config").get("startScore").asInt()).isEqualTo(401);

        Map<String, Object> ch = ChallengesIT.challenge(b.id());
        ch.put("startScore", 401);
        Response challenge = a.client().post("/api/challenges", ch);
        assertThat(challenge.status()).as("%s", challenge.body()).isEqualTo(201);
        assertThat(challenge.body().get("startScore").asInt()).isEqualTo(401);
    }
}
