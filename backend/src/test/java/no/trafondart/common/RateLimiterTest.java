package no.trafondart.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private static final RateLimiter.Rule TWO_PER_10S = new RateLimiter.Rule("t", 2, Duration.ofSeconds(10));

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock, nanos::get);

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
        clock.now = clock.now.plus(d);
    }

    @Test
    void allowsCapacityThenRefusesWithARetryAfterHint() {
        limiter.check(TWO_PER_10S, "k");
        limiter.check(TWO_PER_10S, "k");
        assertThatThrownBy(() -> limiter.check(TWO_PER_10S, "k"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(((RateLimiter.Limited) e.details()).retryAfterSeconds()).isBetween(1L, 5L);
                });
    }

    @Test
    void tokensRefillOverTheWindow() {
        limiter.check(TWO_PER_10S, "k");
        limiter.check(TWO_PER_10S, "k");
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isPositive();
        advance(Duration.ofSeconds(5)); // one token back
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isZero();
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isPositive();
        advance(Duration.ofHours(1)); // never more than the capacity
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isZero();
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isZero();
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isPositive();
    }

    @Test
    void refusedAttemptsDoNotDigTheHoleDeeper() {
        limiter.check(TWO_PER_10S, "k");
        limiter.check(TWO_PER_10S, "k");
        for (int i = 0; i < 50; i++) limiter.tryConsume(TWO_PER_10S, "k");
        advance(Duration.ofSeconds(5));
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isZero();
    }

    @Test
    void keysAndRulesAreIndependent() {
        RateLimiter.Rule other = new RateLimiter.Rule("other", 1, Duration.ofMinutes(1));
        limiter.check(TWO_PER_10S, "a");
        limiter.check(TWO_PER_10S, "a");
        limiter.check(TWO_PER_10S, "b");
        limiter.check(other, "a");
        assertThat(limiter.tryConsume(TWO_PER_10S, "a")).isPositive();
    }

    @Test
    void resetForgetsAKey() {
        limiter.check(TWO_PER_10S, "k");
        limiter.check(TWO_PER_10S, "k");
        limiter.reset(TWO_PER_10S, "k");
        assertThat(limiter.tryConsume(TWO_PER_10S, "k")).isZero();
    }

    @Test
    void idleBucketsAreEvictedButBusyOnesAreKept() {
        limiter.check(TWO_PER_10S, "idle");
        limiter.check(TWO_PER_10S, "idle");
        advance(Duration.ofHours(3));
        limiter.check(TWO_PER_10S, "busy");
        limiter.check(TWO_PER_10S, "busy");
        limiter.evictIdle();
        assertThat(limiter.size()).isEqualTo(1);
        assertThat(limiter.tryConsume(TWO_PER_10S, "busy")).isPositive();
    }

    @Test
    void spoofedClientAddressesBehindOneEdgeAreStillLimited() {
        // An attacker who reaches the backend directly can put anything in X-Forwarded-For, but not the
        // address the request really came from (the "edge" entry). That edge gets a larger shared budget.
        int allowed = 0;
        for (int i = 0; i < 100; i++) {
            try {
                limiter.check(TWO_PER_10S, new ClientIpResolver.ClientAddress("10.0.0." + i, "203.0.113.9"));
                allowed++;
            } catch (ApiException e) {
                assertThat(e.code()).isEqualTo(ErrorCode.RATE_LIMITED);
            }
        }
        assertThat(allowed).isEqualTo(TWO_PER_10S.capacity() * RateLimiter.EDGE_FACTOR);
    }

    @Test
    void sameClientIsLimitedOnItsOwnBudgetFirst() {
        var me = new ClientIpResolver.ClientAddress("198.51.100.7", "203.0.113.9");
        limiter.check(TWO_PER_10S, me);
        limiter.check(TWO_PER_10S, me);
        assertThatThrownBy(() -> limiter.check(TWO_PER_10S, me)).isInstanceOf(ApiException.class);
        // others behind the same edge are unaffected
        limiter.check(TWO_PER_10S, new ClientIpResolver.ClientAddress("198.51.100.8", "203.0.113.9"));
    }

    @Test
    void neverAllowsMoreThanCapacityUnderConcurrency() throws Exception {
        RateLimiter.Rule ten = new RateLimiter.Rule("c", 10, Duration.ofHours(1));
        RateLimiter real = new RateLimiter();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger ok = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                go.await();
                if (real.tryConsume(ten, "same") == 0) ok.incrementAndGet();
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(10);
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
