package com.medfold.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medfold.records.LabResult;
import com.medfold.records.LabResultRepository;
import com.medfold.storage.StorageProperties;
import com.medfold.support.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LabResultRepository labRepository;
    @Autowired private StorageProperties storageProperties;

    private record Account(UUID id, String token) {
    }

    private Account register() throws Exception {
        String email = "account-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private UUID uploadWithResults(Account account) throws Exception {
        byte[] pdf = ("%PDF-1.4\n% account " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String body = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "r.pdf", "application/pdf", pdf))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(body, "$.id"));
        labRepository.save(new LabResult(documentId, account.id(), 0, "HbA1c", "hba1c", "6.1", "%", null, 1, null));
        return documentId;
    }

    private int count(String table, UUID userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private Path userFolder(UUID userId) {
        return Path.of(storageProperties.root()).resolve(userId.toString());
    }

    private ResultActions deleteAccount(Account account, String password) throws Exception {
        return mockMvc.perform(delete("/api/v1/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"%s\"}".formatted(password)));
    }

    @Test
    void deletingTheAccountRemovesEverythingOfThatUserOnly() throws Exception {
        Account leaving = register();
        Account staying = register();
        uploadWithResults(leaving);
        UUID kept = uploadWithResults(staying);
        assertThat(Files.isDirectory(userFolder(leaving.id()))).isTrue();

        deleteAccount(leaving, PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, leaving.id())).isZero();
        for (String table : List.of("refresh_tokens", "documents", "lab_results", "processing_jobs")) {
            assertThat(count(table, leaving.id())).as(table).isZero();
        }
        assertThat(Files.exists(userFolder(leaving.id()))).as("uploaded files").isFalse();

        assertThat(count("documents", staying.id())).isEqualTo(1);
        assertThat(count("lab_results", staying.id())).isEqualTo(1);
        assertThat(Files.isDirectory(userFolder(staying.id()))).isTrue();
        mockMvc.perform(get("/api/v1/documents/" + kept).header(HttpHeaders.AUTHORIZATION, "Bearer " + staying.token()))
                .andExpect(status().isOk());

        // The old access token is still signed, but it now belongs to nobody.
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + leaving.token()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void aWrongOrMissingPasswordDeletesNothing() throws Exception {
        Account account = register();
        uploadWithResults(account);

        deleteAccount(account, "not my password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        deleteAccount(account, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("Enter your password to confirm"));

        assertThat(count("documents", account.id())).isEqualTo(1);
        assertThat(Files.isDirectory(userFolder(account.id()))).isTrue();
    }
}
