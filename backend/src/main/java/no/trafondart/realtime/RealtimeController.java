package no.trafondart.realtime;

import no.trafondart.auth.AuthUser;
import no.trafondart.auth.JwtService;
import no.trafondart.common.ApiException;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ws")
public class RealtimeController {

    private final JwtService jwt;
    private final UserRepository users;

    public RealtimeController(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    public record Ticket(String ticket, long expiresInSeconds) {
    }

    /**
     * Issues a 60-second ticket for the STOMP CONNECT frame. POST (and therefore CSRF-protected) so another site
     * cannot mint tickets on the user's behalf.
     */
    @PostMapping("/ticket")
    public Ticket ticket() {
        User user = users.findById(AuthUser.current().id()).orElseThrow(ApiException::notFound);
        return new Ticket(jwt.issueWsTicket(user.getId(), user.getTokenVersion()), JwtService.WS_TICKET_TTL.toSeconds());
    }
}
