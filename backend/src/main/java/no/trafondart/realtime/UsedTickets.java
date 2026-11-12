package no.trafondart.realtime;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which WebSocket tickets have been used, so each ticket opens exactly one connection. A ticket lives
 * 60 seconds, so entries are dropped a little after that. One backend instance → in memory is enough.
 */
@Component
public class UsedTickets {

    private static final Duration KEEP = Duration.ofMinutes(2);

    private final ConcurrentHashMap<String, Instant> used = new ConcurrentHashMap<>();
    private final Clock clock;

    public UsedTickets() {
        this(Clock.systemUTC());
    }

    UsedTickets(Clock clock) {
        this.clock = clock;
    }

    /** @return true the first time a ticket id is seen, false for every replay (and for a missing id). */
    public boolean markUsed(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) return false;
        Instant now = clock.instant();
        if (used.size() > 1000) used.values().removeIf(t -> t.isBefore(now.minus(KEEP)));
        return used.putIfAbsent(ticketId, now) == null;
    }
}
