package no.trafondart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * All TrafonDart-specific settings. Every secret comes from an environment variable – see
 * {@code backend/.env.example} and docs/DEPLOYMENT.md. Nothing secret has a default here.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cookie cookie,
        Websocket websocket,
        Security security,
        BootstrapAdmin bootstrapAdmin
) {

    /**
     * @param secret base64 or plain string, at least 32 bytes. Required outside the dev profile.
     * @param ttl    lifetime of a login session
     */
    public record Jwt(String secret, @DefaultValue("14d") Duration ttl) {
    }

    /** @param secure send cookies with the Secure flag (must be true in production / HTTPS) */
    public record Cookie(@DefaultValue("true") boolean secure) {
    }

    /** @param allowedOrigins origins allowed to open the STOMP WebSocket, e.g. https://trafondart.vercel.app */
    public record Websocket(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /** @param trustedProxyHops number of reverse proxies we control in front of the app (Vercel + Render = 2) */
    public record Security(@DefaultValue("0") int trustedProxyHops) {
    }

    /** Creates the first admin on start-up when the users table is empty. Leave blank afterwards. */
    public record BootstrapAdmin(String username, String password, @DefaultValue("Administrator") String realName) {
    }
}
