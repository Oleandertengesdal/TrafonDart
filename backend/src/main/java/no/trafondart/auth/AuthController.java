package no.trafondart.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import no.trafondart.common.ApiException;
import no.trafondart.common.ClientIpResolver;
import no.trafondart.common.RateLimiter;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos;
import no.trafondart.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final InviteService invites;
    private final JwtService jwt;
    private final SessionCookies cookies;
    private final ClientIpResolver ipResolver;
    private final RateLimiter rateLimiter;
    private final UserRepository users;
    private final PasswordResetService resets;

    public AuthController(AuthService auth, InviteService invites, JwtService jwt, SessionCookies cookies,
                          ClientIpResolver ipResolver, RateLimiter rateLimiter, UserRepository users,
                          PasswordResetService resets) {
        this.resets = resets;
        this.auth = auth;
        this.invites = invites;
        this.jwt = jwt;
        this.cookies = cookies;
        this.ipResolver = ipResolver;
        this.rateLimiter = rateLimiter;
        this.users = users;
    }

    /** Makes sure the XSRF-TOKEN cookie is set. The SPA calls this once on start-up. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public UserDtos.Me login(@Valid @RequestBody AuthService.LoginRequest req, HttpServletRequest request,
                             HttpServletResponse response) {
        User user = auth.login(req, ipResolver.resolveAddress(request));
        startSession(user, response);
        return UserDtos.Me.of(user);
    }

    @PostMapping("/register")
    public UserDtos.Me register(@Valid @RequestBody AuthService.RegisterRequest req, HttpServletRequest request,
                                HttpServletResponse response) {
        User user = auth.register(req, ipResolver.resolveAddress(request));
        startSession(user, response);
        return UserDtos.Me.of(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    /** Revokes every session of the current user, on every device. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletResponse response) {
        auth.logoutEverywhere(AuthUser.current().id());
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDtos.Me me() {
        return users.findById(AuthUser.current().id()).map(UserDtos.Me::of).orElseThrow(ApiException::notFound);
    }

    @PostMapping("/change-password")
    public UserDtos.Me changePassword(@Valid @RequestBody UserDtos.ChangePasswordRequest req,
                                      HttpServletResponse response) {
        User user = auth.changePassword(AuthUser.current().id(), req);
        // Other devices are logged out; this one gets a fresh token.
        startSession(user, response);
        return UserDtos.Me.of(user);
    }

    /** Lets the registration page tell the user early whether their invite code works. Rate-limited per IP. */
    @GetMapping("/invites/{code}")
    public InviteService.InviteCheck checkInvite(@PathVariable String code, HttpServletRequest request) {
        rateLimiter.check(RateLimiter.INVITE_CHECK_PER_IP, ipResolver.resolveAddress(request));
        if (code.length() > 40) return new InviteService.InviteCheck(false, "INVITE_INVALID", null);
        return invites.check(code);
    }

    /** Is this reset link still usable? Token in the body (not the URL) so it never lands in access logs. */
    @PostMapping("/password-reset/check")
    public PasswordResetService.LinkCheck checkReset(@Valid @RequestBody PasswordResetService.TokenRequest req,
                                                     HttpServletRequest request) {
        return resets.check(req.token(), ipResolver.resolveAddress(request));
    }

    /** Sets a new password from a one-time admin link and logs the person in on this device. */
    @PostMapping("/password-reset")
    public UserDtos.Me resetPassword(@Valid @RequestBody PasswordResetService.ResetRequest req,
                                     HttpServletRequest request, HttpServletResponse response) {
        User user = resets.reset(req, ipResolver.resolveAddress(request));
        startSession(user, response);
        return UserDtos.Me.of(user);
    }

    private void startSession(User user, HttpServletResponse response) {
        cookies.write(response, jwt.issueSession(user.getId(), user.getTokenVersion()), jwt.sessionTtl());
    }
}
