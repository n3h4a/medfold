package com.medfold.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String aliceToken;
    private UUID alice;
    private String bobToken;

    @BeforeEach
    void createUsers() {
        User a = userRepository.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "h", null));
        User b = userRepository.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "h", null));
        alice = a.getId();
        aliceToken = jwtService.issueAccessToken(a).value();
        bobToken = jwtService.issueAccessToken(b).value();
    }

    private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private UUID upload(String token, String filename, byte[] content) throws Exception {
        String body = as(token, multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID uploadPdf(String token, String filename) throws Exception {
        return upload(token, filename, TestFiles.pdf(UUID.randomUUID().toString()));
    }

    private void setDocDate(UUID id, LocalDate date) {
        jdbcTemplate.update("UPDATE documents SET doc_date = ? WHERE id = ?", date, id);
    }

    @Test
    void listsOnlyOwnDocumentsInTimelineOrder() throws Exception {
        UUID old = uploadPdf(aliceToken, "old.pdf");
        UUID undated = uploadPdf(aliceToken, "undated.pdf");
        UUID recent = uploadPdf(aliceToken, "recent.pdf");
        uploadPdf(bobToken, "bobs.pdf");
        setDocDate(old, LocalDate.of(2022, 5, 1));
        setDocDate(recent, LocalDate.of(2025, 9, 1));

        as(aliceToken, get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].id").value(recent.toString()))
                .andExpect(jsonPath("$.items[1].id").value(old.toString()))
                .andExpect(jsonPath("$.items[2].id").value(undated.toString()))
                .andExpect(jsonPath("$.items[0].storageKey").doesNotExist());
    }

    @Test
    void paginatesAndCapsPageSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            uploadPdf(aliceToken, "doc" + i + ".pdf");
        }

        as(aliceToken, get("/api/v1/documents").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        as(aliceToken, get("/api/v1/documents").param("size", "5000"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void ownerCanReadDocumentAndStatus() throws Exception {
        UUID id = uploadPdf(aliceToken, "cbc.pdf");

        as(aliceToken, get("/api/v1/documents/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("cbc.pdf"));
        as(aliceToken, get("/api/v1/documents/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void ownerCanStreamOriginalFileWithSafeHeaders() throws Exception {
        byte[] bytes = TestFiles.pdf("stream me " + UUID.randomUUID());
        UUID id = upload(aliceToken, "lab report.pdf", bytes);

        as(aliceToken, get("/api/v1/documents/" + id + "/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("inline;")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void otherUsersDocumentLooksExactlyLikeAMissingOne() throws Exception {
        UUID alicesDoc = uploadPdf(aliceToken, "private.pdf");
        UUID random = UUID.randomUUID();

        for (String suffix : new String[] {"", "/status", "/file"}) {
            String forOther = as(bobToken, get("/api/v1/documents/" + alicesDoc + suffix))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            String forMissing = as(bobToken, get("/api/v1/documents/" + random + suffix))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            assertThat(forOther).as("GET %s", suffix).isEqualTo(forMissing).contains("DOCUMENT_NOT_FOUND");
        }
        as(bobToken, delete("/api/v1/documents/" + alicesDoc)).andExpect(status().isNotFound());

        as(aliceToken, get("/api/v1/documents/" + alicesDoc)).andExpect(status().isOk());
    }

    @Test
    void deleteRemovesRowAndFile() throws Exception {
        UUID id = uploadPdf(aliceToken, "to-delete.pdf");
        Path file = Path.of("target/test-uploads").resolve(
                documentRepository.findByIdAndUserId(id, alice).orElseThrow().getStorageKey());
        assertThat(file).exists();

        as(aliceToken, delete("/api/v1/documents/" + id)).andExpect(status().isNoContent());

        assertThat(documentRepository.findByIdAndUserId(id, alice)).isEmpty();
        assertThat(file).doesNotExist();
        as(aliceToken, delete("/api/v1/documents/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void deletedFileCanBeUploadedAgain() throws Exception {
        byte[] bytes = TestFiles.pdf("reupload " + UUID.randomUUID());
        UUID first = upload(aliceToken, "a.pdf", bytes);
        as(aliceToken, delete("/api/v1/documents/" + first)).andExpect(status().isNoContent());

        upload(aliceToken, "a.pdf", bytes);
    }

    @Test
    void missingFileOnDiskGivesClearError() throws Exception {
        UUID id = uploadPdf(aliceToken, "vanishing.pdf");
        Files.delete(Path.of("target/test-uploads").resolve(
                documentRepository.findByIdAndUserId(id, alice).orElseThrow().getStorageKey()));

        as(aliceToken, get("/api/v1/documents/" + id + "/file"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void invalidIdIsBadRequest() throws Exception {
        as(aliceToken, get("/api/v1/documents/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/documents/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/documents/" + id + "/file")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/documents/" + id)).andExpect(status().isUnauthorized());
    }
}
