package no.trafondart.it;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Browser errors are sent to our own backend, which passes them on to Sentry (if configured). */
class ClientErrorsIT extends IntegrationTestBase {

    static Map<String, Object> report() {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "error");
        m.put("message", "TypeError: x is undefined");
        m.put("stack", "TypeError: x is undefined\n    at Scorer (https://trafondart.example/assets/index-abc.js:1:2345)");
        m.put("path", "/matches/123");
        m.put("release", "1.4.0");
        return m;
    }

    @Test
    void anyoneCanReportAnErrorEvenBeforeLoggingIn() {
        ApiClient anon = client();
        assertThat(anon.post("/api/client-errors", report()).status()).isEqualTo(202);
        Member m = member("errLogged");
        assertThat(m.client().post("/api/client-errors", report()).status()).isEqualTo(202);
    }

    @Test
    void reportsNeedTheCsrfTokenAndJson() {
        ApiClient anon = client();
        anon.sendCsrf = false;
        assertThat(anon.post("/api/client-errors", report()).status()).isEqualTo(403);
        ApiClient c = client();
        assertThat(c.raw("POST", "/api/client-errors", "message=hi", "application/x-www-form-urlencoded").status()).isEqualTo(415);
    }

    @Test
    void oversizedOrOddReportsAreRefused() {
        ApiClient c = client();
        Map<String, Object> big = report();
        big.put("message", "x".repeat(501));
        assertThat(c.post("/api/client-errors", big).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> stack = report();
        stack.put("stack", "x".repeat(8001));
        assertThat(c.post("/api/client-errors", stack).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> kind = report();
        kind.put("kind", "anything");
        assertThat(c.post("/api/client-errors", kind).code()).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> none = report();
        none.remove("message");
        assertThat(c.post("/api/client-errors", none).code()).isEqualTo("VALIDATION_FAILED");
        String huge = "{\"kind\":\"error\",\"message\":\"m\",\"stack\":\"" + "y".repeat(70_000) + "\"}";
        assertThat(c.raw("POST", "/api/client-errors", huge, "application/json").status()).isIn(400, 413);
    }

    @Test
    void aBrokenPageCannotFloodTheReporter() {
        ApiClient c = client();
        int last = 0;
        for (int i = 0; i < 11; i++) last = c.post("/api/client-errors", report()).status();
        assertThat(last).isEqualTo(429);
        // others are not affected
        assertThat(client().post("/api/client-errors", report()).status()).isEqualTo(202);
    }
}
