package no.trafondart.match;

import java.util.UUID;

/**
 * Domain events. "Lifecycle" events are handled synchronously inside the same transaction (ratings,
 * tournament progression); "broadcast" events are sent to WebSocket subscribers after the commit.
 */
public final class MatchEvents {

    private MatchEvents() {
    }

    // lifecycle (in-transaction)
    public record MatchCompleted(Match match) {
    }

    public record MatchAbandoned(Match match) {
    }

    public record MatchVoided(Match match) {
    }

    // broadcast (after commit)
    public record MatchUpdated(MatchViews.LiveMatchView view) {
    }

    /** The set of live / recently finished matches changed (home page feed). */
    public record FeedChanged(UUID matchId) {
    }
}
