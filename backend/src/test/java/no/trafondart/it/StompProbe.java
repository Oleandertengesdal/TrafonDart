package no.trafondart.it;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** A minimal STOMP 1.2 client over Java's WebSocket, enough to test the read-only live socket. */
final class StompProbe implements WebSocket.Listener {
    private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();
    private volatile boolean closed;
    private WebSocket ws;

    static StompProbe connect(int port, String ticket) throws Exception {
        StompProbe p = connectRaw(port, ticket);
        assertThat(p.nextFrame()).startsWith("CONNECTED");
        return p;
    }

    static StompProbe connectRaw(int port, String ticket) throws Exception {
        StompProbe p = open(port, null);
        p.sendConnect(ticket);
        return p;
    }

    /** Opens the WebSocket only (no STOMP CONNECT yet). {@code origin} may be null. */
    static StompProbe open(int port, String origin) throws Exception {
        StompProbe p = new StompProbe();
        WebSocket.Builder b = HttpClient.newHttpClient().newWebSocketBuilder();
        if (origin != null) b.header("Origin", origin);
        p.ws = b.buildAsync(URI.create("ws://localhost:" + port + "/ws"), p).get(5, TimeUnit.SECONDS);
        return p;
    }

    void sendConnect(String ticket) throws Exception {
        String headers = "accept-version:1.2\nhost:localhost\nheart-beat:0,0\n" + (ticket == null ? "" : "ticket:" + ticket + "\n");
        ws.sendText("CONNECT\n" + headers + "\n\u0000", true).get(5, TimeUnit.SECONDS);
    }

    void subscribe(String destination) throws Exception {
        subscribe("sub-0", destination);
    }

    void subscribe(String id, String destination) throws Exception {
        ws.sendText("SUBSCRIBE\nid:" + id + "\ndestination:" + destination + "\n\n\u0000", true).get(5, TimeUnit.SECONDS);
        Thread.sleep(200);
    }

    void send(String destination, String body) throws Exception {
        ws.sendText("SEND\ndestination:" + destination + "\ncontent-type:text/plain\n\n" + body + "\u0000", true).get(5, TimeUnit.SECONDS);
    }

    String nextFrame() throws InterruptedException {
        String f = frames.poll(10, TimeUnit.SECONDS);
        assertThat(f).as("expected a STOMP frame").isNotNull();
        return f;
    }

    /**
     * True when the server refused what was just sent: an ERROR frame arrived, or the server closed the socket
     * (it may close before the ERROR frame is flushed). False if neither happened within 10 s.
     */
    boolean rejected() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            String f = frames.poll(100, TimeUnit.MILLISECONDS);
            if (f != null) return f.startsWith("ERROR");
            if (closed) return true;
        }
        return false;
    }

    /** The next frame within {@code millis}, or null. */
    String pollFrame(long millis) throws InterruptedException {
        return frames.poll(millis, TimeUnit.MILLISECONDS);
    }

    JsonNode nextMessage() throws InterruptedException {
        String f;
        do {
            f = nextFrame();
        } while (!f.startsWith("MESSAGE"));
        String body = f.substring(f.indexOf("\n\n") + 2).replace("\u0000", "");
        return JsonMapper.builder().build().readTree(body);
    }

    boolean isClosed() {
        return closed;
    }

    void close() {
        ws.abort();
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            String text = partial.toString();
            partial.setLength(0);
            if (!text.isBlank()) frames.add(text);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closed = true;
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        closed = true;
    }
}
