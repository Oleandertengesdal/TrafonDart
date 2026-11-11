package no.trafondart.common;

import jakarta.servlet.http.HttpServletRequest;
import no.trafondart.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Finds the real client IP for rate limiting.
 *
 * <p>{@code X-Forwarded-For} is attacker-controlled at its left end, so we count from the right and skip exactly
 * {@code app.security.trusted-proxy-hops} entries added by proxies we control. Behind Vercel → Render that is 2;
 * running locally with no proxy it is 0 (use the socket address).
 */
@Component
public class ClientIpResolver {

    private final int trustedHops;

    public ClientIpResolver(AppProperties props) {
        this.trustedHops = props.security().trustedProxyHops();
    }

    public String resolve(HttpServletRequest request) {
        if (trustedHops <= 0) return request.getRemoteAddr();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) return request.getRemoteAddr();
        String[] parts = xff.split(",");
        int index = parts.length - trustedHops;
        if (index < 0) index = 0;
        return parts[index].trim();
    }
}
