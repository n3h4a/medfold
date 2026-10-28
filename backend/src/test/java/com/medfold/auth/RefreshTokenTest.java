package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void activeWhenNotRevokedAndNotExpired() {
        RefreshToken token = new RefreshToken(null, "x".repeat(64), NOW.plusSeconds(60));

        assertThat(token.isActive(NOW)).isTrue();
    }

    @Test
    void inactiveOnceExpired() {
        RefreshToken token = new RefreshToken(null, "x".repeat(64), NOW);

        assertThat(token.isActive(NOW)).isFalse();
    }

    @Test
    void inactiveOnceRevokedAndRevokeKeepsFirstTimestamp() {
        RefreshToken token = new RefreshToken(null, "x".repeat(64), NOW.plusSeconds(60));

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(30));

        assertThat(token.isActive(NOW)).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }
}
