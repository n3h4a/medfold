package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import com.medfold.user.User;

class JwtServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("unit-test-secret-0123456789abcdef0123456789", Duration.ofMinutes(15), "medfold");

    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSigningKey(PROPERTIES);
    private final JwtDecoder decoder = config.jwtDecoder(key, PROPERTIES);

    private static User userWithId(UUID id) {
        User user = new User("asha@example.com", "hash", "Asha");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void issuesTokenWithUserIdSubjectEmailAndExpiry() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtService service = new JwtService(config.jwtEncoder(key), PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();

        JwtService.AccessToken token = service.issueAccessToken(userWithId(userId));
        Jwt decoded = decoder.decode(token.value());

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("asha@example.com");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("medfold");
        assertThat(decoded.getIssuedAt()).isEqualTo(now);
        assertThat(decoded.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(token.expiresAt()).isEqualTo(decoded.getExpiresAt());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProperties other = new JwtProperties("another-secret-0123456789abcdef0123456789", Duration.ofMinutes(15), "medfold");
        SecretKey otherKey = config.jwtSigningKey(other);
        JwtService otherService = new JwtService(config.jwtEncoder(otherKey), other, Clock.systemUTC());

        String forged = otherService.issueAccessToken(userWithId(UUID.randomUUID())).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Instant longAgo = Instant.now().minus(Duration.ofHours(2));
        JwtService service = new JwtService(config.jwtEncoder(key), PROPERTIES, Clock.fixed(longAgo, ZoneOffset.UTC));

        String expired = service.issueAccessToken(userWithId(UUID.randomUUID())).value();

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }
}
