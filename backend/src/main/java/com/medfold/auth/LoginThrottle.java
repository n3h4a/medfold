package com.medfold.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.medfold.common.ApiException;

/**
 * Slows down password guessing. Wrong passwords are counted per account and per client address
 * in a sliding window; once a limit is reached, further attempts are refused with 429 before any
 * password is checked. Unknown emails are counted exactly like real ones, so the limit reveals
 * nothing about which accounts exist.
 *
 * Counts live in the database, so they hold across restarts and every backend instance. Keys are
 * stored hashed, and rows older than the window are deleted every few minutes.
 */
@Component
public class LoginThrottle {

    private static final String RECENT = """
            SELECT failed_at FROM login_failures
            WHERE throttle_key = ? AND failed_at > ?
            ORDER BY failed_at DESC
            LIMIT ?
            """;

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;

    public LoginThrottle(LoginThrottleProperties properties, Clock clock, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
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
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.batchUpdate("INSERT INTO login_failures (throttle_key, failed_at) VALUES (?, ?)",
                List.of(new Object[] { hash(accountKey), now }, new Object[] { hash(addressKey), now }));
    }

    /** A correct password clears the account's count (not the address's: one success shouldn't hide guessing). */
    public void recordSuccess(String accountKey) {
        jdbcTemplate.update("DELETE FROM login_failures WHERE throttle_key = ?", hash(accountKey));
    }

    /** Deletes failures that have left the window, so the table stays small. */
    @Scheduled(fixedDelayString = "PT5M")
    public void evictExpired() {
        jdbcTemplate.update("DELETE FROM login_failures WHERE failed_at <= ?",
                Timestamp.from(clock.instant().minus(properties.window())));
    }

    int failuresOf(String key) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM login_failures WHERE throttle_key = ?", Integer.class,
                hash(key));
    }

    private Duration waitFor(String key, int limit, Instant now) {
        List<Instant> recent = jdbcTemplate.query(RECENT, (rs, row) -> rs.getTimestamp(1).toInstant(), hash(key),
                Timestamp.from(now.minus(properties.window())), limit);
        if (recent.size() < limit) {
            return Duration.ZERO;
        }
        // Allowed again when the oldest of the last `limit` failures leaves the window.
        return Duration.between(now, recent.getLast().plus(properties.window()));
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
