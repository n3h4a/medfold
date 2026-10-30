package com.medfold.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.medfold.common.ApiException;

/**
 * Slows down password guessing. Wrong passwords are counted per account and per client address
 * in a sliding window; once a limit is reached, further attempts are refused with 429 before any
 * password is checked. Unknown emails are counted exactly like real ones, so the limit reveals
 * nothing about which accounts exist.
 *
 * Counts are kept in memory: enough for one backend instance, and a restart only forgives.
 */
@Component
public class LoginThrottle {

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public static String accountKey(String emailOrId) {
        return "account:" + emailOrId.strip().toLowerCase(Locale.ROOT);
    }

    public static String addressKey(String address) {
        return "address:" + address;
    }

    /** @throws ApiException 429 TOO_MANY_ATTEMPTS, with retryAfterSeconds, if either key is over its limit */
    public void check(String accountKey, String addressKey) {
        Instant now = clock.instant();
        Duration wait = max(waitFor(accountKey, properties.maxFailuresPerAccount(), now),
                waitFor(addressKey, properties.maxFailuresPerAddress(), now));
        if (!wait.isZero()) {
            long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
            long minutes = (seconds + 59) / 60;
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again in " + minutes + (minutes == 1 ? " minute" : " minutes"),
                    Map.of("retryAfterSeconds", seconds));
        }
    }

    public void recordFailure(String accountKey, String addressKey) {
        Instant now = clock.instant();
        for (String key : new String[] { accountKey, addressKey }) {
            Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (times) {
                prune(times, now);
                times.addLast(now);
            }
        }
    }

    /** A correct password clears the account's count (not the address's: one success shouldn't hide guessing). */
    public void recordSuccess(String accountKey) {
        failures.remove(accountKey);
    }

    /** Forgets counts that have left the window, so memory stays bounded. */
    @Scheduled(fixedDelayString = "PT5M")
    public void evictExpired() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    int trackedKeys() {
        return failures.size();
    }

    private Duration waitFor(String key, int limit, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return Duration.ZERO;
        }
        synchronized (times) {
            prune(times, now);
            if (times.size() < limit) {
                return Duration.ZERO;
            }
            // Allowed again when enough of the oldest failures have left the window.
            Instant oldestCounted = times.stream().skip(times.size() - limit).findFirst().orElseThrow();
            return Duration.between(now, oldestCounted.plus(properties.window()));
        }
    }

    private void prune(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(properties.window());
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
