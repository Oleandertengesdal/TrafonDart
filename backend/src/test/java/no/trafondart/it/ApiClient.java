package no.trafondart.it;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny browser-like client: keeps cookies and sends the CSRF header the way the React app does.
 *
 * <p>The test profile trusts two proxy hops (like production: Vercel → Render), so every client gets its own
 * made-up {@code X-Forwarded-For} chain. That keeps per-IP rate limits of one test from affecting another, and
 * lets security tests forge the header on purpose.
 */
final class ApiClient {

    record Response(int status, JsonNode body) {
        String code() {
            return body != null && body.has("code") ? body.get("code").asString() : null;
        }
    }

    record Raw(int status, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        List<String> all(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).toList();
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger IPS = new AtomicInteger();

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    boolean sendCsrf = true;
    /** Sent as X-Forwarded-For: "client, edge". Null sends no header at all. */
    String forwardedFor;

    ApiClient(int port) {
        this.base = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
        int n = IPS.incrementAndGet();
        this.forwardedFor = "198.18." + (n / 250) + "." + (n % 250 + 1) + ", 100.64." + (n / 250) + "." + (n % 250 + 1);
    }

    Response get(String path) {
        return send(builder(path).GET());
    }

    Response post(String path, Object body) {
        return send(withJson(builder(path), body, "POST"));
    }

    Response put(String path, Object body) {
        return send(withJson(builder(path), body, "PUT"));
    }

    Response patch(String path, Object body) {
        return send(withJson(builder(path), body, "PATCH"));
    }

    Response delete(String path) {
        return send(withJson(builder(path), null, "DELETE"));
    }

    Response delete(String path, Object body) {
        return send(withJson(builder(path), body, "DELETE"));
    }

    /** Any method, any raw body and content type, extra headers as name/value pairs. Returns headers too. */
    Raw raw(String method, String path, String body, String contentType, String... headers) {
        HttpRequest.Builder b = builder(path);
        if (!method.equals("GET") && cookie("XSRF-TOKEN") == null) get("/api/auth/csrf");
        if (contentType != null) b.header("Content-Type", contentType);
        String token = cookie("XSRF-TOKEN");
        if (sendCsrf && token != null && !method.equals("GET")) b.header("X-XSRF-TOKEN", token);
        for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Raw(res.statusCode(), res.headers().map(), res.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest.Builder builder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (forwardedFor != null) b.header("X-Forwarded-For", forwardedFor);
        return b;
    }

    private HttpRequest.Builder withJson(HttpRequest.Builder b, Object body, String method) {
        if (cookie("XSRF-TOKEN") == null) get("/api/auth/csrf");
        String json = body == null ? "{}" : JSON.writeValueAsString(body);
        b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        String token = cookie("XSRF-TOKEN");
        if (sendCsrf && token != null) b.header("X-XSRF-TOKEN", token);
        return b;
    }

    String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElse(null);
    }

    void setCookie(String name, String value) {
        HttpCookie c = new HttpCookie(name, value);
        c.setPath("/");
        c.setVersion(0);
        cookies.getCookieStore().add(URI.create(base), c);
    }

    void clearCookies() {
        cookies.getCookieStore().removeAll();
    }

    private Response send(HttpRequest.Builder b) {
        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = res.body() == null || res.body().isBlank() ? null : JSON.readTree(res.body());
            return new Response(res.statusCode(), node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Response login(String username, String password) {
        return post("/api/auth/login", Map.of("username", username, "password", password));
    }
}
