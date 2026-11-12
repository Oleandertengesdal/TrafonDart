package no.trafondart.realtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StompTopicsTest {

    @ParameterizedTest
    @ValueSource(strings = {"/topic/live", "/topic/matches/0b6f6f6e-7b1a-4c43-9f3a-1d2e3f4a5b6c",
            "/topic/matches/0b6f6f6e-7b1a-4c43-9f3a-1d2e3f4a5b6c/social", "/topic/tournaments/0B6F6F6E-7B1A-4C43-9F3A-1D2E3F4A5B6C"})
    void knownTopicsAreAllowed(String topic) {
        assertThat(StompAuthInterceptor.isAllowedTopic(topic)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/live/", "/topic/live/../admin", "/topic/matches/abc", "/topic/matches/*", "/topic/**",
            "/topic/matches/0b6f6f6e-7b1a-4c43-9f3a-1d2e3f4a5b6c/other", "/queue/x", "/user/queue/errors", "", "/topic/LIVE",
            "/topic/tournaments/0b6f6f6e-7b1a-4c43-9f3a-1d2e3f4a5b6c/social", " /topic/live", "/topic/live\n"})
    void everythingElseIsRefused(String topic) {
        assertThat(StompAuthInterceptor.isAllowedTopic(topic)).isFalse();
        assertThat(StompAuthInterceptor.isAllowedTopic(null)).isFalse();
    }

    @Test
    void ticketsWorkOnce() {
        UsedTickets used = new UsedTickets(Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC));
        assertThat(used.markUsed("a")).isTrue();
        assertThat(used.markUsed("a")).isFalse();
        assertThat(used.markUsed("b")).isTrue();
        assertThat(used.markUsed(null)).isFalse();
        assertThat(used.markUsed(" ")).isFalse();
    }
}
