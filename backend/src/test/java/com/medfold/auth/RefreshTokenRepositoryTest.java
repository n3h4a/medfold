package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RefreshTokenRepositoryTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User newUser(String email) {
        return userRepository.saveAndFlush(new User(email, "hash", null));
    }

    @Test
    void savesAndFindsTokenByHash() {
        User user = newUser("token@example.com");
        Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS);
        refreshTokenRepository.saveAndFlush(new RefreshToken(user, HASH_A, expiry));

        RefreshToken found = refreshTokenRepository.findByTokenHash(HASH_A).orElseThrow();

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getRevokedAt()).isNull();
    }

    @Test
    void rejectsDuplicateTokenHash() {
        User user = newUser("dup-token@example.com");
        Instant expiry = Instant.now().plus(1, ChronoUnit.DAYS);
        refreshTokenRepository.saveAndFlush(new RefreshToken(user, HASH_A, expiry));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(new RefreshToken(user, HASH_A, expiry)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserDeletesTheirTokens() {
        User user = newUser("cascade@example.com");
        refreshTokenRepository.saveAndFlush(
                new RefreshToken(user, HASH_B, Instant.now().plus(1, ChronoUnit.DAYS)));
        entityManager.clear();

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, user.getId()))
                .isZero();
    }
}
