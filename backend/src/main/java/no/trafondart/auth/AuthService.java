package no.trafondart.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.common.RateLimiter;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos;
import no.trafondart.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    /** BCrypt only looks at the first 72 bytes; longer passwords would silently be truncated. */
    public static final int MAX_PASSWORD_BYTES = 72;
    public static final int MIN_PASSWORD_LENGTH = 10;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final InviteService invites;
    private final RateLimiter rateLimiter;
    /** Used to spend the same BCrypt time whether or not the username exists (no user enumeration by timing). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, InviteService invites, RateLimiter rateLimiter) {
        this.users = users;
        this.encoder = encoder;
        this.invites = invites;
        this.rateLimiter = rateLimiter;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-real-password");
    }

    public record LoginRequest(@NotBlank @Size(max = 24) String username, @NotBlank @Size(max = 200) String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 40) String inviteCode,
            @NotBlank @Pattern(regexp = UserDtos.USERNAME_PATTERN) String username,
            @NotBlank @Size(max = 60) @Pattern(regexp = UserDtos.REAL_NAME_PATTERN) String realName,
            @NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_BYTES) String password,
            @Pattern(regexp = "^(nb|en)$") String preferredLocale) {
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest req, String clientIp) {
        String usernameKey = req.username().toLowerCase(Locale.ROOT);
        rateLimiter.check(RateLimiter.LOGIN_PER_IP, clientIp);
        rateLimiter.check(RateLimiter.LOGIN_PER_USERNAME, usernameKey);

        User user = users.findByUsernameIgnoreCase(req.username()).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash);
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        rateLimiter.reset(RateLimiter.LOGIN_PER_USERNAME, usernameKey);
        return user;
    }

    @Transactional
    public User register(RegisterRequest req, String clientIp) {
        rateLimiter.check(RateLimiter.REGISTER_PER_IP, clientIp);
        requirePasswordBytes(req.password());
        invites.findUsable(req.inviteCode());
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }
        User user = new User(req.username(), req.realName().trim(), encoder.encode(req.password()), User.Role.USER);
        user.setPreferredLocale(req.preferredLocale());
        users.saveAndFlush(user);
        invites.redeem(req.inviteCode(), user.getId());
        return user;
    }

    @Transactional
    public User changePassword(Long userId, UserDtos.ChangePasswordRequest req) {
        rateLimiter.check(RateLimiter.PASSWORD_CHANGE_PER_USER, String.valueOf(userId));
        requirePasswordBytes(req.newPassword());
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_INCORRECT);
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.revokeSessions();
        return user;
    }

    @Transactional
    public User logoutEverywhere(Long userId) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        user.revokeSessions();
        return user;
    }

    private static void requirePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
