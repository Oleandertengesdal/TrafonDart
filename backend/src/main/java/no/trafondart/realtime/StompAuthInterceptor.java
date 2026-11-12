package no.trafondart.realtime;

import no.trafondart.auth.AuthUser;
import no.trafondart.auth.JwtService;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Guards the STOMP inbound channel.
 * <ul>
 *   <li>CONNECT must carry a valid {@code ticket} header (a 60-second, ws-audience JWT from /api/ws/ticket).</li>
 *   <li>SUBSCRIBE is only allowed to the known public topics.</li>
 *   <li>SEND is always rejected – the socket is read-only.</li>
 * </ul>
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ALLOWED_TOPICS = Pattern.compile(
            "^/topic/(live|matches/[0-9a-fA-F-]{36}(/social)?|tournaments/[0-9a-fA-F-]{36}|games/[0-9a-fA-F-]{36})$");

    private final JwtService jwt;
    private final UserRepository users;
    private final UsedTickets usedTickets;

    public StompAuthInterceptor(JwtService jwt, UserRepository users, UsedTickets usedTickets) {
        this.jwt = jwt;
        this.users = users;
        this.usedTickets = usedTickets;
    }

    /** True for the topics a client may subscribe to. */
    static boolean isAllowedTopic(String destination) {
        return destination != null && ALLOWED_TOPICS.matcher(destination).matches();
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> {
                String ticket = accessor.getFirstNativeHeader("ticket");
                User user = jwt.verify(ticket, JwtService.AUD_WS)
                        .filter(v -> usedTickets.markUsed(v.tokenId())) // one connection per ticket
                        .flatMap(v -> users.findById(v.userId())
                                .filter(User::isEnabled)
                                .filter(u -> u.getTokenVersion() == v.tokenVersion()))
                        .orElseThrow(() -> new MessageDeliveryException("AUTH_REQUIRED"));
                AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.getRole().name());
                accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            }
            case SUBSCRIBE -> {
                if (accessor.getUser() == null) throw new MessageDeliveryException("AUTH_REQUIRED");
                String dest = accessor.getDestination();
                if (!isAllowedTopic(dest)) {
                    throw new MessageDeliveryException("FORBIDDEN");
                }
            }
            case SEND -> throw new MessageDeliveryException("FORBIDDEN");
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK, NACK: fine
            }
        }
        return message;
    }
}
