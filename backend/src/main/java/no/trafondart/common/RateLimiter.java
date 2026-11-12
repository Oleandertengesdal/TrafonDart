package no.trafondart.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory token-bucket rate limiter.
 *
 * <p>Good enough for a single backend instance (the free Render tier runs exactly one). If you ever scale out,
 * move the buckets to Redis/Postgres – see docs/SECURITY.md.
 */
@Component
public class RateLimiter {

    /** A named limit: {@code capacity} requests, refilled evenly over {@code window}. */
    public record Rule(String name, int capacity, Duration window) {
        Rule scaled(String suffix, int factor) {
            return new Rule(name + suffix, capacity * factor, window);
        }
    }

    public record Limited(long retryAfterSeconds) {
    }

    public static final Rule LOGIN_PER_IP = new Rule("login-ip", 20, Duration.ofMinutes(5));
    public static final Rule LOGIN_PER_USERNAME = new Rule("login-user", 6, Duration.ofMinutes(5));
    public static final Rule REGISTER_PER_IP = new Rule("register-ip", 10, Duration.ofHours(1));
    public static final Rule INVITE_CHECK_PER_IP = new Rule("invite-check-ip", 30, Duration.ofHours(1));
    public static final Rule RESET_PER_IP = new Rule("reset-ip", 20, Duration.ofHours(1));
    public static final Rule PASSWORD_CHANGE_PER_USER = new Rule("password-user", 10, Duration.ofHours(1));

    /**
     * Everyone behind one edge address shares this many times the per-client budget. Through Vercel the edge is
     * one of Vercel's servers (a whole group fits easily); for someone calling the backend directly it is their own
     * address, so forging client addresses in X-Forwarded-For buys them at most this factor.
     */
    public static final int EDGE_FACTOR = 5;

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
        long lastUsedMillis;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final LongSupplier nanoTime;

    public RateLimiter() {
        this(Clock.systemUTC(), System::nanoTime);
    }

    RateLimiter(Clock clock, LongSupplier nanoTime) {
        this.clock = clock;
        this.nanoTime = nanoTime;
    }

    /** Consumes one token or throws {@code RATE_LIMITED} with a Retry-After hint. */
    public void check(Rule rule, String key) {
        long wait = tryConsume(rule, key);
        if (wait > 0) {
            throw new ApiException(ErrorCode.RATE_LIMITED, new Limited(wait));
        }
    }

    /** Per-client limit plus a larger shared limit for everything arriving through the same edge address. */
    public void check(Rule rule, ClientIpResolver.ClientAddress address) {
        check(rule, address.client());
        if (!address.edge().equals(address.client())) {
            check(rule.scaled("-edge", EDGE_FACTOR), address.edge());
        }
    }

    /** @return 0 if allowed, otherwise seconds until the next token. */
    long tryConsume(Rule rule, String key) {
        String k = rule.name() + ":" + key;
        long now = nanoTime.getAsLong();
        double refillPerNano = rule.capacity() / (double) rule.window().toNanos();
        long[] result = {0};
        buckets.compute(k, (ignored, b) -> {
            if (b == null) {
                b = new Bucket();
                b.tokens = rule.capacity();
                b.lastRefillNanos = now;
            } else {
                b.tokens = Math.min(rule.capacity(), b.tokens + (now - b.lastRefillNanos) * refillPerNano);
                b.lastRefillNanos = now;
            }
            b.lastUsedMillis = clock.millis();
            if (b.tokens >= 1) {
                b.tokens -= 1;
            } else {
                result[0] = Math.max(1, (long) Math.ceil((1 - b.tokens) / refillPerNano / 1_000_000_000.0));
            }
            return b;
        });
        return result[0];
    }

    /** Forget a key, e.g. reset the per-username login bucket after a successful login. */
    public void reset(Rule rule, String key) {
        buckets.remove(rule.name() + ":" + key);
    }

    int size() {
        return buckets.size();
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void evictIdle() {
        long cutoff = clock.millis() - Duration.ofHours(2).toMillis();
        buckets.entrySet().removeIf(e -> e.getValue().lastUsedMillis < cutoff);
    }
}
