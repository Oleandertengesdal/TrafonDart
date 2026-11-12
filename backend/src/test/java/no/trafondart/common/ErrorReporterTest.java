package no.trafondart.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Error alerts go to Sentry in its envelope format, scrubbed of anything personal or secret. */
class ErrorReporterTest {

    record Received(String path, Map<String, List<String>> headers, String body) {
        JsonNode event() {
            String[] lines = body.split("\n");
            return JsonMapper.builder().build().readTree(lines[2]);
        }
    }

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private MutableClock clock;

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-12T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(ex.getRequestURI().getPath(), ex.getRequestHeaders(), body));
            ex.sendResponseHeaders(status.get(), -1);
            ex.close();
        });
        server.start();
        clock = new MutableClock();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String dsn() {
        return "http://pubkey123@127.0.0.1:" + server.getAddress().getPort() + "/42";
    }

    private ErrorReporter reporter(String dsn) {
        return new ErrorReporter(dsn, "test", "1.4.0", clock, Runnable::run);
    }

    @Test
    void withoutADsnNothingIsSent() {
        ErrorReporter r = reporter("");
        assertThat(r.enabled()).isFalse();
        r.report(new IllegalStateException("boom"), "GET", "/api/x");
        assertThat(received).isEmpty();
        assertThat(reporter(null).enabled()).isFalse();
    }

    @Test
    void aBadDsnDisablesReportingInsteadOfCrashing() {
        for (String bad : new String[]{"not a url", "https://example.com/1", "https://key@example.com/", "ftp://k@example.com/1",
                "https://key@/1"}) {
            ErrorReporter r = reporter(bad);
            assertThat(r.enabled()).as(bad).isFalse();
            r.report(new RuntimeException("x"), "GET", "/");
        }
        assertThat(received).isEmpty();
    }

    @Test
    void serverErrorsAreSentAsSentryEnvelopes() {
        ErrorReporter r = reporter(dsn());
        assertThat(r.enabled()).isTrue();
        r.report(new IllegalStateException("database fell over"), "POST", "/api/matches/abc/throws");
        assertThat(received).hasSize(1);
        Received got = received.getFirst();
        assertThat(got.path()).isEqualTo("/api/42/envelope/");
        String auth = got.headers().get("X-sentry-auth").getFirst();
        assertThat(auth).contains("sentry_version=7").contains("sentry_key=pubkey123").contains("sentry_client=trafondart/");
        String[] lines = got.body().split("\n");
        assertThat(lines).hasSize(3);
        JsonNode header = JsonMapper.builder().build().readTree(lines[0]);
        JsonNode item = JsonMapper.builder().build().readTree(lines[1]);
        JsonNode event = got.event();
        assertThat(header.get("event_id").asString()).matches("[0-9a-f]{32}").isEqualTo(event.get("event_id").asString());
        assertThat(item.get("type").asString()).isEqualTo("event");
        assertThat(event.get("level").asString()).isEqualTo("error");
        assertThat(event.get("platform").asString()).isEqualTo("java");
        assertThat(event.get("environment").asString()).isEqualTo("test");
        assertThat(event.get("release").asString()).isEqualTo("trafondart@1.4.0");
        assertThat(event.get("tags").get("source").asString()).isEqualTo("backend");
        JsonNode ex = event.get("exception").get("values").get(0);
        assertThat(ex.get("type").asString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(ex.get("value").asString()).isEqualTo("database fell over");
        assertThat(ex.get("stacktrace").get("frames").size()).isPositive();
        assertThat(event.get("request").get("method").asString()).isEqualTo("POST");
        assertThat(event.get("request").get("url").asString()).isEqualTo("/api/matches/abc/throws");
        // no personal data at all
        assertThat(event.has("user")).isFalse();
        assertThat(got.body()).doesNotContain("ip_address", "cookie", "Cookie");
    }

    @Test
    void secretsAndPersonalDataAreScrubbed() {
        ErrorReporter r = reporter(dsn());
        // a fake, harmless JWT, assembled at runtime so secret scanners don't mistake the test for a leak
        String jwtHeader = "eyJ" + "hbGciOiJIUzI1NiJ9";
        String jwt = jwtHeader + "." + "eyJ" + "zdWIiOiIxIn0" + "." + "c2lnbmF0dXJlLXZhbHVl";
        String nasty = "Failed for ole.nordmann@example.com at /reset-password?token=SuperSecretResetToken123#frag "
                + "with jwt " + jwt + " and header Bearer abcdef123456 password=hunter22";
        r.report(new RuntimeException(nasty), "GET", "/api/auth/password-reset?token=AnotherSecret999");
        String body = received.getFirst().body();
        assertThat(body).doesNotContain("ole.nordmann", "SuperSecretResetToken123", jwtHeader, "abcdef123456",
                "hunter22", "AnotherSecret999", "frag");
        assertThat(body).contains("Failed for").contains("/reset-password");
        assertThat(received.getFirst().event().get("request").get("url").asString()).isEqualTo("/api/auth/password-reset");
    }

    @Test
    void browserErrorsAreSentWithTheirOwnPlatformAndScrubbed() {
        ErrorReporter r = reporter(dsn());
        r.reportClient(new ErrorReporter.ClientError("unhandledrejection", "Network down for kari@example.no",
                "Error: x\n    at f (https://app.example/assets/i.js?v=1:1:2)\n    at g (https://app.example/assets/i.js:3:4)",
                "/reset-password?token=Leaky123", "1.4.0", "Mozilla/5.0 Test"));
        JsonNode event = received.getFirst().event();
        assertThat(event.get("platform").asString()).isEqualTo("javascript");
        assertThat(event.get("tags").get("source").asString()).isEqualTo("frontend");
        assertThat(event.get("tags").get("kind").asString()).isEqualTo("unhandledrejection");
        assertThat(event.get("release").asString()).isEqualTo("trafondart@1.4.0");
        assertThat(event.get("request").get("url").asString()).isEqualTo("/reset-password");
        assertThat(received.getFirst().body()).doesNotContain("kari@example.no", "Leaky123", "?v=1");
        assertThat(event.get("exception").get("values").get(0).get("value").asString()).contains("Network down");
    }

    @Test
    void aStormOfErrorsIsCapped() {
        ErrorReporter r = reporter(dsn());
        for (int i = 0; i < ErrorReporter.MAX_PER_MINUTE + 25; i++) r.report(new RuntimeException("again " + i), "GET", "/");
        assertThat(received).hasSize(ErrorReporter.MAX_PER_MINUTE);
        clock.now = clock.now.plus(Duration.ofMinutes(1)).plusSeconds(1);
        r.report(new RuntimeException("later"), "GET", "/");
        assertThat(received).hasSize(ErrorReporter.MAX_PER_MINUTE + 1);
    }

    @Test
    void sentryBeingDownNeverBreaksTheApp() {
        status.set(500);
        ErrorReporter r = reporter(dsn());
        r.report(new RuntimeException("x"), "GET", "/");
        server.stop(0);
        r.report(new RuntimeException("y"), "GET", "/");
        // no exception means success
    }

    @Test
    void causesAreIncludedInnermostLast() {
        ErrorReporter r = reporter(dsn());
        r.report(new RuntimeException("outer", new IllegalArgumentException("inner")), "GET", "/");
        JsonNode values = received.getFirst().event().get("exception").get("values");
        // Sentry wants the root cause first and the outermost exception last
        assertThat(values.get(0).get("type").asString()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(values.get(values.size() - 1).get("value").asString()).isEqualTo("outer");
    }
}
