package no.trafondart.user;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import no.trafondart.awards.AwardService;
import no.trafondart.auth.InviteService;
import no.trafondart.auth.PasswordResetService;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.match.MatchService;
import no.trafondart.match.MatchViews;
import no.trafondart.rating.RatingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin-only endpoints. Access is enforced in SecurityConfig ({@code /api/admin/** → ROLE_ADMIN}). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final InviteService invites;
    private final MatchService matches;
    private final RatingService ratings;
    private final PasswordResetService resets;
    private final AwardService awards;

    public AdminController(UserRepository users, InviteService invites, MatchService matches, RatingService ratings,
                           PasswordResetService resets, AwardService awards) {
        this.awards = awards;
        this.resets = resets;
        this.users = users;
        this.invites = invites;
        this.matches = matches;
        this.ratings = ratings;
    }

    // -------------------------------------------------------------- users

    @GetMapping("/users")
    public List<UserDtos.AdminUserView> users() {
        return users.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(UserDtos.AdminUserView::of).toList();
    }

    @PatchMapping("/users/{id}")
    @Transactional
    public UserDtos.AdminUserView updateUser(@PathVariable Long id, @Valid @RequestBody UserDtos.AdminUpdateUserRequest req) {
        AuthUser me = AuthUser.current();
        User user = users.findById(id).orElseThrow(ApiException::notFound);
        if (user.getId().equals(me.id())) {
            // Prevent admins from locking themselves out.
            throw new ApiException(ErrorCode.CANNOT_CHANGE_SELF);
        }
        if (user.isDeleted()) throw new ApiException(ErrorCode.ACCOUNT_DELETED);
        boolean wasActiveAdmin = user.isAdmin() && user.isEnabled();
        if (req.role() != null) user.setRole(User.Role.valueOf(req.role()));
        if (req.enabled() != null) user.setEnabled(req.enabled());
        boolean isActiveAdmin = user.isAdmin() && user.isEnabled();
        if (wasActiveAdmin && !isActiveAdmin && users.countByRoleAndEnabledTrue(User.Role.ADMIN) < 1) {
            throw new ApiException(ErrorCode.LAST_ADMIN);
        }
        // Any change in role or access ends the user's current sessions.
        user.revokeSessions();
        return UserDtos.AdminUserView.of(user);
    }

    /**
     * Creates a one-time password reset link (valid 24 h) for a member who forgot their password. The response
     * contains the token once; the frontend turns it into {@code /reset-password?token=…} for the admin to send.
     */
    @PostMapping("/users/{id}/password-reset")
    @ResponseStatus(HttpStatus.CREATED)
    public PasswordResetService.CreatedLink passwordReset(@PathVariable Long id) {
        return resets.create(AuthUser.current().id(), id);
    }

    // -------------------------------------------------------------- invites

    @GetMapping("/invites")
    public List<InviteService.InviteView> invites() {
        return invites.list();
    }

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteService.CreatedInvite createInvite(@Valid @RequestBody InviteService.CreateInviteRequest req) {
        return invites.create(AuthUser.current().id(), req);
    }

    @PostMapping("/invites/{id}/revoke")
    public ResponseEntity<Void> revokeInvite(@PathVariable Long id) {
        invites.revoke(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- disputes & ratings

    @GetMapping("/disputes")
    public List<MatchViews.DisputeView> disputes() {
        return matches.listDisputes();
    }

    @PostMapping("/disputes/{id}/resolve")
    public MatchViews.DisputeView resolve(@PathVariable Long id, @Valid @RequestBody MatchViews.ResolveDisputeRequest req) {
        return matches.resolveDispute(id, AuthUser.current(), req);
    }

    /** Rebuilds every rating from scratch. Monthly awards depend on rating gains, so they are re-decided too. */
    @PostMapping("/ratings/recompute")
    public Map<String, Integer> recompute() {
        int n = ratings.recomputeAll();
        awards.recomputeAll();
        return Map.of("matches", n);
    }
}
