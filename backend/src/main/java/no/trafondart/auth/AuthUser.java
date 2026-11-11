package no.trafondart.auth;

import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The authenticated principal stored in the security context. */
public record AuthUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** The current user, or {@code AUTH_REQUIRED}. */
    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.AUTH_REQUIRED);
    }
}
