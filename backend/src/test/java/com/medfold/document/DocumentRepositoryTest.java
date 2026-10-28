package com.medfold.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void createUsers() {
        alice = userRepository.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "h", null)).getId();
        bob = userRepository.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "h", null)).getId();
    }

    private Document save(UUID owner, String sha, LocalDate docDate) {
        Document document = new Document(owner, "report.pdf", owner + "/" + UUID.randomUUID() + ".pdf",
                "application/pdf", 1024, sha);
        document.setDocDate(docDate);
        return documentRepository.saveAndFlush(document);
    }

    private static String sha(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    void savesWithDefaultsAndGeneratedFields() {
        Document saved = save(alice, sha('a'), null);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(saved.isUserEdited()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void ownerCanFindTheirDocumentButNoOneElseCan() {
        Document document = save(alice, sha('a'), null);

        assertThat(documentRepository.findByIdAndUserId(document.getId(), alice)).isPresent();
        assertThat(documentRepository.findByIdAndUserId(document.getId(), bob)).isEmpty();
    }

    @Test
    void sameFileTwiceIsRejectedForOneUserButAllowedForAnother() {
        save(alice, sha('c'), null);
        save(bob, sha('c'), null);

        assertThat(documentRepository.findByUserIdAndSha256(alice, sha('c'))).isPresent();
        assertThatThrownBy(() -> save(alice, sha('c'), null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void timelineIsNewestRecordFirstUndatedLastAndOnlyOwnDocuments() {
        Document older = save(alice, sha('1'), LocalDate.of(2023, 3, 1));
        Document undated = save(alice, sha('2'), null);
        Document newer = save(alice, sha('3'), LocalDate.of(2025, 1, 15));
        save(bob, sha('4'), LocalDate.of(2026, 1, 1));

        var page = documentRepository.findTimeline(alice, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Document::getId)
                .containsExactly(newer.getId(), older.getId(), undated.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(documentRepository.countByUserId(bob)).isEqualTo(1);
    }

    @Test
    void persistsStatusTransitionsAndMetadata() {
        Document document = save(alice, sha('5'), null);
        document.markProcessing();
        document.setDocType(DocumentType.LAB_REPORT);
        document.setTitle("Complete Blood Count");
        document.markReady();
        documentRepository.saveAndFlush(document);
        entityManager.clear();

        Document reloaded = documentRepository.findByIdAndUserId(document.getId(), alice).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(reloaded.getDocType()).isEqualTo(DocumentType.LAB_REPORT);
        assertThat(reloaded.getTitle()).isEqualTo("Complete Blood Count");
    }

    // One rejected statement per test: PostgreSQL aborts the rest of the test's transaction after an error.
    @Test
    void databaseRejectsUnknownStatus() {
        Document document = save(alice, sha('6'), null);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE documents SET status = 'DONE' WHERE id = ?", document.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownDocumentType() {
        Document document = save(alice, sha('8'), null);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE documents SET doc_type = 'X_RAY' WHERE id = ?", document.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserDeletesTheirDocuments() {
        save(alice, sha('7'), null);
        entityManager.clear();

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", alice);

        assertThat(documentRepository.countByUserId(alice)).isZero();
    }
}
