package com.medfold.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medfold.support.TestcontainersConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesUserWithNormalizedEmailAndGeneratedFields() {
        User saved = userRepository.saveAndFlush(new User("  Asha.Rao@Example.COM ", "hash", "Asha Rao"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("asha.rao@example.com");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("asha@example.com", "hash", "Asha"));

        assertThat(userRepository.findByEmailNormalized("ASHA@example.com")).isPresent();
        assertThat(userRepository.existsByEmailNormalized(" Asha@Example.com")).isTrue();
        assertThat(userRepository.findByEmailNormalized("someone@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateEmailWithDifferentCase() {
        userRepository.saveAndFlush(new User("dup@example.com", "hash", null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("DUP@example.com", "hash", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsEmailThatIsNotNormalized() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES ('Mixed@Example.com', 'hash')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
