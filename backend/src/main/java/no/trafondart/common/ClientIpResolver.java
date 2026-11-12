package no.trafondart.common;

import jakarta.servlet.http.HttpServletRequest;
import no.trafondart.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the client address for rate limiting.
 *
 * <p>{@code X-Forwarded-For} is attacker-controlled at its left end, so we count from the right and skip exactly
 * {@code app.security.trusted-proxy-hops} entries added by proxies we control. Behind Vercel → Render that is 2;
 * running locally with no proxy it is 0 (use the socket address).
 *
 * <p>Two addresses come back:
 * <ul>
 *   <li>{@code client} – the browser, as reported by our outermost proxy (Vercel).</li>
 *   <li>{@code edge} – whoever actually connected to our innermost proxy (Render). Through the normal path that is
 *   Vercel; for someone calling the Render URL directly it is their real address, which they cannot forge. The
 *   rate limiter gives each edge a larger shared budget, so rotating fake client addresses doesn't help.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    public record ClientAddress(String client, String edge) {
    }

    /** IPv4 or IPv6 text form (no ports, no garbage). Anything else is not trusted as a rate-limit key. */
    private static final Pattern ADDRESS = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final int MAX_HEADER_ENTRIES = 32;

    private final int trustedHops;

    public ClientIpResolver(AppProperties props) {
        this.trustedHops = props.security() == null ? 0 : props.security().trustedProxyHops();
    }

    public ClientAddress resolveAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedHops <= 0) return new ClientAddress(remote, remote);
        List<String> chain = rightmostEntries(request.getHeader("X-Forwarded-For"));
        if (chain.isEmpty()) return new ClientAddress(remote, remote);

        String edge = chain.getLast();
        if (!ADDRESS.matcher(edge).matches()) edge = remote;
        int index = Math.max(0, chain.size() - trustedHops);
        String client = chain.get(index);
        if (!ADDRESS.matcher(client).matches()) client = edge;
        return new ClientAddress(client, edge);
    }

    /** The client address only, for logs and simple per-client keys. */
    public String resolve(HttpServletRequest request) {
        return resolveAddress(request).client();
    }

    /** Non-empty entries, keeping at most the last {@link #MAX_HEADER_ENTRIES} (only the right end matters). */
    private static List<String> rightmostEntries(String header) {
        List<String> out = new ArrayList<>();
        if (header == null || header.isBlank()) return out;
        String[] parts = header.split(",");
        for (int i = Math.max(0, parts.length - MAX_HEADER_ENTRIES); i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }
}
