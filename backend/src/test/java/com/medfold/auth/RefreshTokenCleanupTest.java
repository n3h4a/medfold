package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenCleanupTest {

    @Autowired private RefreshTokenCleanup cleanup;
    @Autowired private RefreshTokenRepository repository;
    @Autowired private UserRepository userRepository;

    private RefreshToken token(User user, Instant expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken(user, UUID.randomUUID().toString().replace("-", ""), expiresAt);
        if (revoked) {
            token.revoke(Instant.now().minus(Duration.ofDays(3)));
        }
        return repository.saveAndFlush(token);
    }

    @Test
    void deletesOnlyTokensExpiredForMoreThanADay() {
        User user = userRepository.saveAndFlush(new User("cleanup-" + UUID.randomUUID() + "@example.com", "h", null));
        Instant now = Instant.now();
        RefreshToken longExpired = token(user, now.minus(Duration.ofDays(2)), false);
        RefreshToken revokedAndExpired = token(user, now.minus(Duration.ofDays(5)), true);
        RefreshToken justExpired = token(user, now.minus(Duration.ofHours(2)), false);
        RefreshToken revokedButValid = token(user, now.plus(Duration.ofDays(3)), true);
        RefreshToken active = token(user, now.plus(Duration.ofDays(7)), false);

        assertThat(cleanup.purgeExpired()).isGreaterThanOrEqualTo(2);

        assertThat(repository.findById(longExpired.getId())).isEmpty();
        assertThat(repository.findById(revokedAndExpired.getId())).isEmpty();
        assertThat(repository.findById(justExpired.getId())).as("within the grace day").isPresent();
        assertThat(repository.findById(revokedButValid.getId())).as("kept for reuse detection").isPresent();
        assertThat(repository.findById(active.getId())).isPresent();
    }
}
