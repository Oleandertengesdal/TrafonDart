package no.trafondart.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import no.trafondart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies HS256 JWTs.
 *
 * <p>Two audiences are used so a token cannot be replayed in the wrong place:
 * {@code session} (the login cookie) and {@code ws} (a 60-second WebSocket ticket).
 */
@Service
public class JwtService {

    public static final String AUD_SESSION = "session";
    public static final String AUD_WS = "ws";
    public static final Duration WS_TICKET_TTL = Duration.ofSeconds(60);
    private static final String ISSUER = "trafondart";
    private static final int MIN_SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final byte[] secret;
    private final Duration sessionTtl;

    public JwtService(AppProperties props, Environment env) {
        this.sessionTtl = props.jwt().ttl();
        this.secret = resolveSecret(props.jwt().secret(), env);
    }

    private static byte[] resolveSecret(String configured, Environment env) {
        if (configured != null && !configured.isBlank()) {
            byte[] bytes = decode(configured.trim());
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
                        + " bytes. Generate one with: openssl rand -base64 48");
            }
            return bytes;
        }
        if (env.acceptsProfiles(Profiles.of("dev", "test"))) {
            log.warn("JWT_SECRET is not set – using a random secret for this run. Sessions end when the app restarts.");
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            return random;
        }
        throw new IllegalStateException("JWT_SECRET is required. Generate one with: openssl rand -base64 48");
    }

    private static byte[] decode(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length >= MIN_SECRET_BYTES) return decoded;
        } catch (IllegalArgumentException ignored) {
            // not base64 – use the raw bytes
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public Duration sessionTtl() {
        return sessionTtl;
    }

    public String issueSession(Long userId, int tokenVersion) {
        return issue(userId, tokenVersion, AUD_SESSION, sessionTtl);
    }

    public String issueWsTicket(Long userId, int tokenVersion) {
        return issue(userId, tokenVersion, AUD_WS, WS_TICKET_TTL);
    }

    private String issue(Long userId, int tokenVersion, String audience, Duration ttl) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .jwtID(UUID.randomUUID().toString())
                .claim("tv", tokenVersion)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign JWT", e);
        }
    }

    public record Verified(Long userId, int tokenVersion, String tokenId) {
    }

    /** Verifies signature, algorithm, issuer, audience and expiry. Empty on any failure. */
    public Optional<Verified> verify(String token, String audience) {
        if (token == null || token.isBlank() || token.length() > 4096) return Optional.empty();
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) return Optional.empty();
            if (!jwt.verify(new MACVerifier(secret))) return Optional.empty();
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(c.getIssuer())) return Optional.empty();
            List<String> aud = c.getAudience();
            if (aud == null || !aud.contains(audience)) return Optional.empty();
            Date exp = c.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) return Optional.empty();
            Long userId = Long.valueOf(c.getSubject());
            Long tv = c.getLongClaim("tv");
            return Optional.of(new Verified(userId, tv == null ? -1 : tv.intValue(), c.getJWTID()));
        } catch (ParseException | JOSEException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
