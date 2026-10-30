package com.medfold.auth;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes refresh tokens a day after they expire, so the table doesn't grow with every login.
 * Revoked tokens stay until they expire: presenting one again is how token theft is detected.
 */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);
    static final Duration GRACE = Duration.ofDays(1);

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Daily at 03:17 server time; several instances running it at once is harmless. */
    @Scheduled(cron = "${medfold.refresh-token.cleanup-cron:0 17 3 * * *}")
    @Transactional
    public int purgeExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant().minus(GRACE));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
        return deleted;
    }
}
