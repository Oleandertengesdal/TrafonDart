package no.trafondart.it;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.WebSocketHandshakeException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The STOMP socket is read-only, ticket-authenticated and limited to a fixed set of topics. */
class LiveSocketIT extends IntegrationTestBase {

    private String ticket(ApiClient c) {
        return c.post("/api/ws/ticket", null).body().get("ticket").asString();
    }

    @Test
    void aValidTicketConnectsAndReceivesEveryDart() throws Exception {
        Member a = member("wsA"), b = member("wsB"), watcher = member("wsW");
        String id = quickMatch(a.client(), a.id(), b.id(), false);
        StompProbe p = StompProbe.connect(port, ticket(watcher.client()));
        p.subscribe("/topic/matches/" + id);
        throwDart(a.client(), id, 20, 3, 0);
        JsonNode pushed = p.nextMessage();
        assertThat(pushed.get("id").asString()).isEqualTo(id);
        assertThat(pushed.get("dartCount").asInt()).isEqualTo(1);
        p.close();
    }

    @Test
    void connectWithoutOrWithAWrongTicketFails() throws Exception {
        Member m = member("wsBad");
        for (String bad : new String[]{null, "garbage", m.client().cookie("td_session")}) {
            StompProbe p = StompProbe.connectRaw(port, bad);
            assertThat(p.nextFrame()).as("ticket %s", bad).startsWith("ERROR");
            p.close();
        }
    }

    @Test
    void aTicketCanOnlyBeUsedOnce() throws Exception {
        Member m = member("wsOnce");
        String t = ticket(m.client());
        StompProbe first = StompProbe.connect(port, t);
        StompProbe replay = StompProbe.connectRaw(port, t);
        assertThat(replay.nextFrame()).startsWith("ERROR");
        first.close();
        replay.close();
    }

    @Test
    void ticketsDieWithTheSession() throws Exception {
        Member m = member("wsRevoke");
        String t = ticket(m.client());
        m.client().post("/api/auth/logout-all", null);
        StompProbe p = StompProbe.connectRaw(port, t);
        assertThat(p.nextFrame()).startsWith("ERROR");
        p.close();
        assertThat(client().post("/api/ws/ticket", null).status()).isEqualTo(401);
    }

    @Test
    void onlyKnownTopicsCanBeSubscribed() throws Exception {
        Member m = member("wsTopic");
        String[] forbidden = {"/topic/admin", "/topic/live/../admin", "/topic/matches/abc", "/topic/matches/*",
                "/topic/**", "/queue/anything", "/user/queue/errors", "/topic/tournaments/" + java.util.UUID.randomUUID() + "/x",
                "/app-disabled/x"};
        for (String dest : forbidden) {
            StompProbe p = StompProbe.connect(port, ticket(m.client()));
            p.subscribe(dest);
            assertThat(p.rejected()).as(dest).isTrue();
            p.close();
        }
        StompProbe ok = StompProbe.connect(port, ticket(m.client()));
        ok.subscribe("/topic/live");
        assertThat(ok.pollFrame(500)).isNull();
        assertThat(ok.isClosed()).as("an allowed topic keeps the socket open").isFalse();
        ok.close();
    }

    @Test
    void clientsCanNeverSend() throws Exception {
        Member m = member("wsSend");
        for (String dest : new String[]{"/topic/live", "/app-disabled/throws", "/topic/matches/" + java.util.UUID.randomUUID()}) {
            StompProbe p = StompProbe.connect(port, ticket(m.client()));
            p.send(dest, "{\"hacked\":true}");
            assertThat(p.rejected()).as(dest).isTrue();
            p.close();
        }
    }

    @Test
    void subscribingBeforeConnectIsRefused() throws Exception {
        StompProbe p = StompProbe.open(port, null);
        p.subscribe("/topic/live");
        assertThat(p.nextFrame()).startsWith("ERROR");
        p.close();
    }

    @Test
    void foreignWebsitesCannotOpenTheSocket() {
        assertThatThrownBy(() -> StompProbe.open(port, "https://evil.example"))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void theTicketEndpointNeedsCsrfLikeAnyOtherPost() {
        Member m = member("wsCsrf");
        m.client().sendCsrf = false;
        assertThat(m.client().post("/api/ws/ticket", Map.of()).code()).isEqualTo("CSRF_INVALID");
    }
}
