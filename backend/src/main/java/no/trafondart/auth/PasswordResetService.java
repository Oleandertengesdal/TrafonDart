package no.trafondart.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.trafondart.common.ApiException;
import no.trafondart.common.ClientIpResolver;
import no.trafondart.common.ErrorCode;
import no.trafondart.common.RateLimiter;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * "Forgot password" without e-mail: an admin creates a one-time link (valid 24 hours) and sends it to the
 * person, e.g. on Messenger. The person picks a new password and is logged in. The admin never sees the
 * password; all of the person's other sessions end and any login lock-out is lifted.
 */
@Service
public class PasswordResetService {

    public static final Duration VALIDITY = Duration.ofHours(24);

    public record CreatedLink(String token, String username, Instant expiresAt) {
    }

    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    public record ResetRequest(@NotBlank @Size(max = 100) String token,
                               @NotBlank @Size(min = AuthService.MIN_PASSWORD_LENGTH, max = AuthService.MAX_PASSWORD_BYTES) String newPassword) {
    }

    public record LinkCheck(boolean valid, String username, String realName, Instant expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, PasswordEncoder encoder,
                                RateLimiter rateLimiter) {
        this.tokens = tokens;
        this.users = users;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public CreatedLink create(Long adminId, Long userId) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (user.isDeleted()) throw new ApiException(no.trafondart.common.ErrorCode.ACCOUNT_DELETED);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expires = Instant.now().plus(VALIDITY);
        tokens.invalidateOpenFor(userId, Instant.now());
        tokens.save(new PasswordResetToken(userId, InviteService.hash(token), expires, adminId));
        return new CreatedLink(token, user.getUsername(), expires);
    }

    @Transactional(readOnly = true)
    public LinkCheck check(String token, ClientIpResolver.ClientAddress client) {
        rateLimiter.check(RateLimiter.RESET_PER_IP, client);
        return tokens.findByTokenHash(InviteService.hash(token))
                .filter(t -> t.usable(Instant.now()))
                .flatMap(t -> users.findById(t.getUserId()).filter(User::isEnabled)
                        .map(u -> new LinkCheck(true, u.getUsername(), u.getRealName(), t.getExpiresAt())))
                .orElse(new LinkCheck(false, null, null, null));
    }

    @Transactional
    public User reset(ResetRequest req, ClientIpResolver.ClientAddress client) {
        rateLimiter.check(RateLimiter.RESET_PER_IP, client);
        if (req.newPassword().getBytes(StandardCharsets.UTF_8).length > AuthService.MAX_PASSWORD_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        PasswordResetToken t = tokens.findByTokenHash(InviteService.hash(req.token()))
                .filter(x -> x.usable(Instant.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESET_LINK_INVALID));
        User check = users.findById(t.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.RESET_LINK_INVALID));
        if (!check.isEnabled()) throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);

        // Atomic: of several requests racing with the same link, exactly one gets to set the password.
        if (tokens.claim(t.getId(), Instant.now()) != 1) throw new ApiException(ErrorCode.RESET_LINK_INVALID);
        User user = users.findById(t.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.RESET_LINK_INVALID));
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.revokeSessions(); // every other device is logged out
        rateLimiter.reset(RateLimiter.LOGIN_PER_USERNAME, user.getUsername().toLowerCase(Locale.ROOT)); // lift lock-out
        return user;
    }
}
