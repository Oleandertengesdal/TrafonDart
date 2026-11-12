package no.trafondart.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import no.trafondart.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every way a session or socket token could be forged, replayed in the wrong place or kept alive too long. */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-test-only";

    private static JwtService service(String secret, Duration ttl, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        AppProperties props = new AppProperties(new AppProperties.Jwt(secret, ttl), null, null, null, null, null, null);
        return new JwtService(props, env);
    }

    private static JwtService service() {
        return service(SECRET, Duration.ofDays(14));
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm alg, byte[] key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(alg), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder validClaims(String audience) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("trafondart").subject("42").audience(audience)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(600))).claim("tv", 3);
    }

    @Test
    void sessionTokenRoundTripsUserAndTokenVersion() {
        JwtService jwt = service();
        var v = jwt.verify(jwt.issueSession(42L, 7), JwtService.AUD_SESSION);
        assertThat(v).isPresent();
        assertThat(v.get().userId()).isEqualTo(42L);
        assertThat(v.get().tokenVersion()).isEqualTo(7);
    }

    @Test
    void audiencesAreNotInterchangeable() {
        JwtService jwt = service();
        assertThat(jwt.verify(jwt.issueWsTicket(1L, 0), JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify(jwt.issueSession(1L, 0), JwtService.AUD_WS)).isEmpty();
    }

    @Test
    void everyTokenHasItsOwnId() {
        JwtService jwt = service();
        var a = jwt.verify(jwt.issueWsTicket(1L, 0), JwtService.AUD_WS).orElseThrow();
        var b = jwt.verify(jwt.issueWsTicket(1L, 0), JwtService.AUD_WS).orElseThrow();
        assertThat(a.tokenId()).isNotBlank().isNotEqualTo(b.tokenId());
    }

    @Test
    void expiredTokensAreRejected() {
        JwtService jwt = service(SECRET, Duration.ofSeconds(-1));
        assertThat(jwt.verify(jwt.issueSession(1L, 0), JwtService.AUD_SESSION)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service();
        String token = jwt.issueSession(1L, 0);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("\"sub\":\"1\"", "\"sub\":\"2\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThat(jwt.verify(forged, JwtService.AUD_SESSION)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService mine = service();
        JwtService theirs = service("another-secret-that-is-long-enough-for-hs256!!", Duration.ofDays(1));
        assertThat(mine.verify(theirs.issueSession(1L, 0), JwtService.AUD_SESSION)).isEmpty();
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                validClaims(JwtService.AUD_SESSION).build().toString().getBytes(StandardCharsets.UTF_8));
        assertThat(service().verify(header + "." + payload + ".", JwtService.AUD_SESSION)).isEmpty();
    }

    @Test
    void otherAlgorithmsAreRejectedEvenWithTheRightKey() throws Exception {
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        String hs512Key = SECRET + SECRET; // HS512 needs a 64-byte key
        String hs512 = sign(validClaims(JwtService.AUD_SESSION).build(), JWSAlgorithm.HS512, hs512Key.getBytes(StandardCharsets.UTF_8));
        assertThat(service(hs512Key, Duration.ofDays(1)).verify(hs512, JwtService.AUD_SESSION)).isEmpty();
        // sanity: the same claims signed HS256 with the right key are accepted
        assertThat(service().verify(sign(validClaims(JwtService.AUD_SESSION).build(), JWSAlgorithm.HS256, key), JwtService.AUD_SESSION)).isPresent();
    }

    @Test
    void wrongIssuerMissingExpiryOrNonNumericSubjectAreRejected() throws Exception {
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        JwtService jwt = service();
        assertThat(jwt.verify(sign(validClaims(JwtService.AUD_SESSION).issuer("evil").build(), JWSAlgorithm.HS256, key), JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify(sign(validClaims(JwtService.AUD_SESSION).expirationTime(null).build(), JWSAlgorithm.HS256, key), JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify(sign(validClaims(JwtService.AUD_SESSION).subject("admin").build(), JWSAlgorithm.HS256, key), JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify(sign(validClaims(JwtService.AUD_SESSION).subject(null).build(), JWSAlgorithm.HS256, key), JwtService.AUD_SESSION)).isEmpty();
    }

    @Test
    void tokenWithoutVersionClaimNeverMatchesARealVersion() throws Exception {
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        var v = service().verify(sign(validClaims(JwtService.AUD_SESSION).claim("tv", null).build(), JWSAlgorithm.HS256, key), JwtService.AUD_SESSION);
        assertThat(v).isPresent();
        assertThat(v.get().tokenVersion()).isNegative();
    }

    @Test
    void garbageNullBlankAndOversizedInputAreRejectedWithoutExceptions() {
        JwtService jwt = service();
        assertThat(jwt.verify(null, JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify("", JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify("not.a.jwt", JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify("a.b", JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify("x".repeat(5000), JwtService.AUD_SESSION)).isEmpty();
        assertThat(jwt.verify("eyJhbGciOiJIUzI1NiJ9.e30.!!!", JwtService.AUD_SESSION)).isEmpty();
    }

    @Test
    void secretRulesAreEnforcedAtStartUp() {
        assertThatThrownBy(() -> service("too-short", Duration.ofDays(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null, Duration.ofDays(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("   ", Duration.ofDays(1), "prod")).isInstanceOf(IllegalStateException.class);
        // dev and test fall back to a random per-run secret
        JwtService dev = service(null, Duration.ofDays(1), "dev");
        assertThat(dev.verify(dev.issueSession(1L, 0), JwtService.AUD_SESSION)).isPresent();
        // a base64 secret of 48 random bytes is accepted
        String b64 = Base64.getEncoder().encodeToString(new byte[48]);
        JwtService fromBase64 = service(b64, Duration.ofDays(1));
        assertThat(fromBase64.verify(fromBase64.issueSession(1L, 0), JwtService.AUD_SESSION)).isPresent();
    }

    @Test
    void twoRandomDevSecretsDoNotAcceptEachOthersTokens() {
        JwtService a = service(null, Duration.ofDays(1), "dev");
        JwtService b = service(null, Duration.ofDays(1), "dev");
        assertThat(b.verify(a.issueSession(1L, 0), JwtService.AUD_SESSION)).isEmpty();
    }
}
