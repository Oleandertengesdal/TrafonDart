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

/** A tiny browser-like client: keeps cookies and sends the CSRF header the way the React app does. */
final class ApiClient {

    record Response(int status, JsonNode body) {
        String code() {
            return body != null && body.has("code") ? body.get("code").asString() : null;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    boolean sendCsrf = true;

    ApiClient(int port) {
        this.base = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    Response post(String path, Object body) {
        return send(withJson(HttpRequest.newBuilder(URI.create(base + path)), body, "POST"));
    }

    Response put(String path, Object body) {
        return send(withJson(HttpRequest.newBuilder(URI.create(base + path)), body, "PUT"));
    }

    Response patch(String path, Object body) {
        return send(withJson(HttpRequest.newBuilder(URI.create(base + path)), body, "PATCH"));
    }

    Response delete(String path) {
        return send(withJson(HttpRequest.newBuilder(URI.create(base + path)), null, "DELETE"));
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
        return post("/api/auth/login", java.util.Map.of("username", username, "password", password));
    }
}
