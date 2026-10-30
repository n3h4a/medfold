package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medfold.common.ApiException;

class LoginThrottleTest {

    /** A clock the test moves by hand. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-30T09:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private LoginThrottle throttle;
    private final String alice = LoginThrottle.accountKey("Alice@Example.com ");
    private final String home = LoginThrottle.addressKey("203.0.113.7");

    @BeforeEach
    void setUp() {
        throttle = new LoginThrottle(new LoginThrottleProperties(5, 8, Duration.ofMinutes(15)), clock);
    }

    private void fail(String account, String address, int times) {
        for (int i = 0; i < times; i++) {
            throttle.check(account, address);
            throttle.recordFailure(account, address);
            clock.now = clock.now.plusSeconds(10);
        }
    }

    @Test
    void anAccountIsRefusedAfterFiveFailuresUntilTheOldestLeavesTheWindow() {
        fail(alice, home, 5);

        ApiException refused = catchThrowableOfType(ApiException.class, () -> throttle.check(alice, home));
        assertThat(refused.getStatus().value()).isEqualTo(429);
        assertThat(refused.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
        assertThat(refused.getMessage()).isEqualTo("Too many failed attempts. Try again in 15 minutes");
        // The first failure was 50s ago; it leaves the 15-minute window in 14m10s.
        assertThat(refused.retryAfterSeconds()).contains(850L);

        assertThat(LoginThrottle.accountKey("alice@example.com")).as("same account however typed").isEqualTo(alice);
        clock.now = clock.now.plus(Duration.ofSeconds(850));
        assertThatCode(() -> throttle.check(alice, home)).doesNotThrowAnyException();
    }

    @Test
    void aSuccessClearsTheAccountButNotTheAddress() {
        fail(alice, home, 4);
        throttle.recordSuccess(alice);
        fail(alice, home, 3);
        assertThatCode(() -> throttle.check(alice, home)).as("3 since the reset, 7 from the address").doesNotThrowAnyException();

        String bob = LoginThrottle.accountKey("bob@example.com");
        fail(bob, home, 1);
        assertThat(catchThrowableOfType(ApiException.class, () -> throttle.check(bob, home)))
                .as("8 failures from one address, whichever accounts").isNotNull();
        assertThatCode(() -> throttle.check(bob, LoginThrottle.addressKey("198.51.100.2"))).doesNotThrowAnyException();
    }

    @Test
    void guessingManyAccountsFromOneAddressIsLimitedToo() {
        for (int i = 0; i < 8; i++) {
            fail(LoginThrottle.accountKey("user" + i + "@example.com"), home, 1);
        }

        ApiException refused = catchThrowableOfType(ApiException.class,
                () -> throttle.check(LoginThrottle.accountKey("someone-new@example.com"), home));
        assertThat(refused.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void expiredCountsAreForgotten() {
        fail(alice, home, 3);
        assertThat(throttle.trackedKeys()).isEqualTo(2);

        clock.now = clock.now.plus(Duration.ofMinutes(16));
        throttle.evictExpired();

        assertThat(throttle.trackedKeys()).isZero();
    }
}
