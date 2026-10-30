package com.medfold.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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
import com.medfold.document.DocumentTerm;
import com.medfold.document.DocumentTermRepository;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecordsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private LabResultRepository labRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private DocumentTermRepository termRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(new User("records-" + UUID.randomUUID() + "@example.com", "h", null));
        token = jwtService.issueAccessToken(user).value();
    }

    private ResultActions call(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private UUID upload() throws Exception {
        byte[] pdf = ("%PDF-1.4\n% records " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String body = call(multipart("/api/v1/documents").file(new MockMultipartFile("file", "r.pdf", "application/pdf", pdf)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void finishJobs(UUID documentId, String documentStatus) {
        jdbcTemplate.update("UPDATE processing_jobs SET status = 'DONE' WHERE document_id = ?", documentId);
        jdbcTemplate.update("UPDATE documents SET status = ?, failure_reason = ? WHERE id = ?", documentStatus,
                documentStatus.equals("FAILED") ? "No text was found in this document" : null, documentId);
    }

    @Test
    void resultsListEverythingExtractedInDocumentOrder() throws Exception {
        UUID id = upload();
        LocalDate date = LocalDate.of(2026, 3, 14);
        LabResult hb = new LabResult(id, user.getId(), 0, "Haemoglobin", "hemoglobin", "12.1", "g/dL", "13.0 - 17.0", 1, date);
        LabFlagCalculator.apply(hb);
        LabResult urine = new LabResult(id, user.getId(), 1, "Urine sugar", "urine_sugar", "Negative", null, null, 2, date);
        labRepository.saveAll(List.of(urine, hb));
        medicationRepository.saveAll(List.of(new Medication(id, user.getId(), 0, "Metformin 500 mg", "500 mg", "1-0-1",
                "oral", "30 days", "after food", date)));
        termRepository.saveAll(List.of(new DocumentTerm(id, user.getId(), 0, "HbA1c", "A blood test for average sugar.")));

        call(get("/api/v1/documents/" + id + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labResults.length()").value(2))
                .andExpect(jsonPath("$.labResults[0].testName").value("Haemoglobin"))
                .andExpect(jsonPath("$.labResults[0].valueText").value("12.1"))
                .andExpect(jsonPath("$.labResults[0].valueNumeric").value(12.1))
                .andExpect(jsonPath("$.labResults[0].refLow").value(13.0))
                .andExpect(jsonPath("$.labResults[0].flag").value("LOW"))
                .andExpect(jsonPath("$.labResults[0].observedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.labResults[1].testName").value("Urine sugar"))
                .andExpect(jsonPath("$.labResults[1].flag").doesNotExist())
                .andExpect(jsonPath("$.medications[0].name").value("Metformin 500 mg"))
                .andExpect(jsonPath("$.medications[0].frequency").value("1-0-1"))
                .andExpect(jsonPath("$.terms[0].term").value("HbA1c"))
                .andExpect(jsonPath("$.labResults[0].userId").doesNotExist());
    }

    @Test
    void anUnprocessedDocumentHasEmptyResults() throws Exception {
        UUID id = upload();

        call(get("/api/v1/documents/" + id + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labResults").isEmpty())
                .andExpect(jsonPath("$.medications").isEmpty())
                .andExpect(jsonPath("$.terms").isEmpty());
    }

    @Test
    void resultsOfAMissingDocumentAre404() throws Exception {
        call(get("/api/v1/documents/" + UUID.randomUUID() + "/results"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void reprocessingAFailedDocumentQueuesItAndClearsTheReason() throws Exception {
        UUID id = upload();
        finishJobs(id, "FAILED");

        call(post("/api/v1/documents/" + id + "/reprocess"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processing_jobs WHERE document_id = ? AND status = 'PENDING'", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void reprocessingIsANoOpWhileADocumentIsAlreadyQueued() throws Exception {
        UUID id = upload();
        finishJobs(id, "READY");

        call(post("/api/v1/documents/" + id + "/reprocess")).andExpect(status().isAccepted());
        call(post("/api/v1/documents/" + id + "/reprocess"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processing_jobs WHERE document_id = ?", Integer.class, id))
                .isEqualTo(2);
    }
}
