package com.medfold.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.common.ApiException;
import com.medfold.user.User;

/**
 * Issues, rotates and revokes refresh tokens. The raw token only ever exists in the
 * client's cookie; the database stores its SHA-256 hash.
 */
@Service
public class RefreshTokenService {

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record Rotation(User user, IssuedRefreshToken refreshToken) {
    }

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.ttl());
        repository.save(new RefreshToken(user, hash(value), expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    /**
     * Exchanges a refresh token for a new one. Presenting a token that was already used
     * means it was probably stolen, so every session of that user is revoked.
     * noRollbackFor keeps that revocation committed even though the request fails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);

        if (token.getRevokedAt() != null) {
            repository.revokeAllActiveForUser(token.getUser().getId(), now);
            throw invalid();
        }
        if (!token.isActive(now)) {
            throw invalid();
        }

        token.revoke(now);
        User user = token.getUser();
        return new Rotation(user, issue(user));
    }

    /** Revokes the token if it exists. Unknown or empty tokens are ignored so logout always succeeds. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Session has expired. Please log in again");
    }
}
