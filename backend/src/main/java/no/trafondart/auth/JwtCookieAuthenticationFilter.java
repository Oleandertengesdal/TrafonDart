package no.trafondart.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request from the session cookie.
 *
 * <p>The user row is loaded on every request so that disabling a user, changing their role or bumping their
 * token version (logout everywhere / password change) takes effect immediately. At friend-group scale this
 * primary-key lookup is negligible.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SessionCookies cookies;
    private final UserRepository users;

    public JwtCookieAuthenticationFilter(JwtService jwtService, SessionCookies cookies, UserRepository users) {
        this.jwtService = jwtService;
        this.cookies = cookies;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.read(request)
                .flatMap(token -> jwtService.verify(token, JwtService.AUD_SESSION))
                .flatMap(v -> users.findById(v.userId())
                        .filter(User::isEnabled)
                        .filter(u -> u.getTokenVersion() == v.tokenVersion()))
                .ifPresent(user -> {
                    AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.getRole().name());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
        chain.doFilter(request, response);
    }
}
