package no.trafondart.common;

import no.trafondart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Error alerts (see docs/OPERATIONS.md): unexpected server errors and browser errors are sent to Sentry when
 * {@code SENTRY_DSN} is set. Without it, nothing leaves the server and errors are only logged.
 *
 * <p>No Sentry SDK and no third-party script: events are posted in Sentry's envelope format with the JDK HTTP client,
 * and browser errors come in through our own {@code POST /api/client-errors}, so the Content-Security-Policy stays
 * as strict as it was. Everything sent is scrubbed first: no user, no IP, no cookies, no query strings, no e-mail
 * addresses, tokens or passwords. At most {@link #MAX_PER_MINUTE} events a minute are sent, so a crash loop can't
 * flood the (free) Sentry quota; sending never blocks or breaks a request.
 */
@Component
public class ErrorReporter {

    private static final Logger log = LoggerFactory.getLogger(ErrorReporter.class);
    public static final int MAX_PER_MINUTE = 30;
    private static final int MAX_FRAMES = 50;
    private static final int MAX_TEXT = 2000;

    /** A browser error as the frontend reports it. */
    public record ClientError(String kind, String message, String stack, String path, String release, String userAgent) {
    }

    private record Target(URI endpoint, String publicKey, String dsn) {
    }

    private final Target target;
    private final String environment;
    private final String release;
    private final String version;
    private final Clock clock;
    private final Executor executor;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private long windowStart;
    private int sentInWindow;

    @Autowired
    public ErrorReporter(AppProperties props) {
        this(props.errors() == null ? null : props.errors().sentryDsn(), props.errors() == null ? null : props.errors().environment(), ErrorReporter.class.getPackage().getImplementationVersion(),
                Clock.systemUTC(), Executors.newVirtualThreadPerTaskExecutor());
    }

    public ErrorReporter(String dsn, String environment, String version, Clock clock, Executor executor) {
        this.target = parse(dsn);
        this.environment = environment == null || environment.isBlank() ? "production" : environment;
        this.version = version == null || version.isBlank() ? "dev" : version;
        this.release = version == null || version.isBlank() ? null : "trafondart@" + version;
        this.clock = clock;
        this.executor = executor;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        if (target != null) log.info("Error alerts are sent to Sentry ({})", target.endpoint().getHost());
    }

    public boolean enabled() {
        return target != null;
    }

    /** An unexpected exception while handling {@code method path}. */
    public void report(Throwable error, String method, String path) {
        if (target == null) return;
        Map<String, Object> event = baseEvent("java", "backend");
        List<Map<String, Object>> values = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable t = error; t != null && seen.add(t) && values.size() < 5; t = t.getCause()) {
            values.add(javaException(t));
        }
        Collections.reverse(values); // Sentry wants the root cause first
        event.put("exception", Map.of("values", values));
        event.put("request", request(method, path));
        send(event);
    }

    public void reportClient(ClientError e) {
        if (target == null) return;
        Map<String, Object> event = baseEvent("javascript", "frontend");
        if (e.release() != null && !e.release().isBlank()) event.put("release", "trafondart@" + scrub(e.release(), 40));
        @SuppressWarnings("unchecked")
        Map<String, Object> tags = (Map<String, Object>) event.get("tags");
        tags.put("kind", e.kind());
        Map<String, Object> ex = new LinkedHashMap<>();
        String message = scrub(e.message(), MAX_TEXT);
        int colon = message.indexOf(':');
        boolean typed = colon > 0 && colon < 40 && message.substring(0, colon).matches("[A-Za-z]+");
        ex.put("type", typed ? message.substring(0, colon) : "Error");
        ex.put("value", typed ? message.substring(colon + 1).strip() : message);
        List<Map<String, Object>> frames = jsFrames(e.stack());
        if (!frames.isEmpty()) ex.put("stacktrace", Map.of("frames", frames));
        event.put("exception", Map.of("values", List.of(ex)));
        event.put("request", request(null, e.path()));
        if (e.userAgent() != null) event.put("contexts", Map.of("browser", Map.of("user_agent", scrub(e.userAgent(), 300))));
        send(event);
    }

    // ---------------------------------------------------------------------------------------------- scrubbing

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern SECRET_PAIR = Pattern.compile("(?i)(password|passord|token|secret|code|ticket|session)([\"']?\\s*[=:]\\s*[\"']?)[^\\s&\"',;]+");
    private static final Pattern QUERY = Pattern.compile("\\?[^\\s#)\"']*");
    private static final Pattern FRAGMENT = Pattern.compile("#[^\\s)\"']*");

    /** Removes anything that could identify a person or unlock something, and caps the length. */
    static String scrub(String text, int max) {
        if (text == null) return "";
        String s = JWT.matcher(text).replaceAll("[jwt]");
        s = BEARER.matcher(s).replaceAll("Bearer [redacted]");
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = SECRET_PAIR.matcher(s).replaceAll("$1$2[redacted]");
        s = QUERY.matcher(s).replaceAll("");
        s = FRAGMENT.matcher(s).replaceAll("");
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---------------------------------------------------------------------------------------------- event

    private Map<String, Object> baseEvent(String platform, String source) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", UUID.randomUUID().toString().replace("-", ""));
        event.put("timestamp", clock.instant().toString());
        event.put("platform", platform);
        event.put("level", "error");
        event.put("logger", source);
        event.put("environment", environment);
        if (release != null) event.put("release", release);
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("source", source);
        event.put("tags", tags);
        return event;
    }

    private static Map<String, Object> request(String method, String path) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (method != null) r.put("method", method);
        r.put("url", scrub(path, 300));
        return r;
    }

    private static Map<String, Object> javaException(Throwable t) {
        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("type", t.getClass().getName());
        ex.put("value", scrub(t.getMessage(), MAX_TEXT));
        List<Map<String, Object>> frames = new ArrayList<>();
        StackTraceElement[] trace = t.getStackTrace();
        // Sentry lists frames oldest first; keep the innermost ones if the trace is long
        for (int i = Math.min(trace.length, MAX_FRAMES) - 1; i >= 0; i--) {
            StackTraceElement el = trace[i];
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("module", el.getClassName());
            f.put("function", el.getMethodName());
            if (el.getFileName() != null) f.put("filename", el.getFileName());
            if (el.getLineNumber() > 0) f.put("lineno", el.getLineNumber());
            f.put("in_app", el.getClassName().startsWith("no.trafondart"));
            frames.add(f);
        }
        if (!frames.isEmpty()) ex.put("stacktrace", Map.of("frames", frames));
        return ex;
    }

    private static final Pattern JS_FRAME = Pattern.compile("^\\s*at\\s+(?:(.+?)\\s+\\()?(.+?):(\\d+):(\\d+)\\)?\\s*$");

    private static List<Map<String, Object>> jsFrames(String stack) {
        List<Map<String, Object>> frames = new ArrayList<>();
        if (stack == null) return frames;
        for (String line : stack.split("\n")) {
            var m = JS_FRAME.matcher(line);
            if (!m.matches()) continue;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("function", m.group(1) == null ? "?" : scrub(m.group(1), 200));
            f.put("filename", scrub(m.group(2), 300));
            f.put("lineno", Integer.parseInt(m.group(3)));
            f.put("colno", Integer.parseInt(m.group(4)));
            frames.add(f);
            if (frames.size() == MAX_FRAMES) break;
        }
        Collections.reverse(frames);
        return frames;
    }

    // ---------------------------------------------------------------------------------------------- sending

    private synchronized boolean allow() {
        long now = clock.millis();
        if (now - windowStart >= 60_000) {
            windowStart = now;
            sentInWindow = 0;
        }
        if (sentInWindow >= MAX_PER_MINUTE) return false;
        sentInWindow++;
        return true;
    }

    private void send(Map<String, Object> event) {
        if (!allow()) {
            log.debug("Error alert dropped: more than {} this minute", MAX_PER_MINUTE);
            return;
        }
        String body;
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("event_id", event.get("event_id"));
            header.put("sent_at", clock.instant().toString());
            header.put("dsn", target.dsn());
            body = json.writeValueAsString(header) + "\n" + json.writeValueAsString(Map.of("type", "event")) + "\n"
                    + json.writeValueAsString(event) + "\n";
        } catch (RuntimeException e) {
            log.warn("Could not build error alert: {}", e.getMessage());
            return;
        }
        String auth = "Sentry sentry_version=7, sentry_key=" + target.publicKey() + ", sentry_client=trafondart/" + version;
        HttpRequest request = HttpRequest.newBuilder(target.endpoint())
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-sentry-envelope")
                .header("X-Sentry-Auth", auth)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            executor.execute(() -> {
                try {
                    HttpResponse<Void> res = http.send(request, HttpResponse.BodyHandlers.discarding());
                    if (res.statusCode() >= 300) log.warn("Sentry answered {} to an error alert", res.statusCode());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("Could not send error alert to Sentry: {}", e.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not queue error alert: {}", e.getMessage());
        }
    }

    /** {@code https://<key>@<host>[:port]/[path/]<project>} → the envelope endpoint, or null if missing or malformed. */
    private static Target parse(String dsn) {
        if (dsn == null || dsn.isBlank()) return null;
        try {
            URI uri = URI.create(dsn.strip());
            String scheme = uri.getScheme();
            String key = uri.getUserInfo();
            String host = uri.getHost();
            String path = uri.getPath();
            if (!"https".equals(scheme) && !"http".equals(scheme)) throw new IllegalArgumentException("scheme");
            if (key == null || key.isBlank() || host == null || path == null) throw new IllegalArgumentException("parts");
            int slash = path.lastIndexOf('/');
            String project = path.substring(slash + 1);
            if (!project.matches("\\d+")) throw new IllegalArgumentException("project");
            String prefix = path.substring(0, slash);
            String publicKey = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
            URI endpoint = new URI(scheme, null, host, uri.getPort(), prefix + "/api/" + project + "/envelope/", null, null);
            return new Target(endpoint, publicKey, dsn.strip());
        } catch (Exception e) {
            log.warn("SENTRY_DSN is not a valid Sentry DSN; error alerts are off");
            return null;
        }
    }
}
