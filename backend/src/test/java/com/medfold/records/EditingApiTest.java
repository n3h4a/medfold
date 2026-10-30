package com.medfold.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.document.Document;
import com.medfold.document.DocumentRepository;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EditingApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private LabResultRepository labRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;
    private UUID documentId;
    private UUID labId;
    private UUID medicationId;

    @BeforeEach
    void setUp() throws Exception {
        user = userRepository.saveAndFlush(new User("edit-" + UUID.randomUUID() + "@example.com", "h", null));
        token = jwtService.issueAccessToken(user).value();
        byte[] pdf = ("%PDF-1.4\n% edit " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String body = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "r.pdf", "application/pdf", pdf))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        documentId = UUID.fromString(JsonPath.read(body, "$.id"));
        jdbcTemplate.update("UPDATE documents SET title = 'CBC', provider_name = 'Dr. Mehat', doc_date = '2026-03-14', "
                + "status = 'READY' WHERE id = ?", documentId);
        LocalDate date = LocalDate.of(2026, 3, 14);
        LabResult lab = new LabResult(documentId, user.getId(), 0, "Haemogl0bin", "hemoglobin", "1.21", "g/dL", "13.0 - 17.0", 1, date);
        LabFlagCalculator.apply(lab);
        labId = labRepository.save(lab).getId();
        medicationId = medicationRepository.saveAll(List.of(new Medication(documentId, user.getId(), 0, "Metf0rmin", "500 mg",
                "1-0-1", "oral", null, null, date))).getFirst().getId();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Document document() {
        return documentRepository.findByIdAndUserId(documentId, user.getId()).orElseThrow();
    }

    @Test
    void correctingDocumentDetailsMarksThemEditedAndRedatesItsResults() throws Exception {
        send(patch("/api/v1/documents/" + documentId),
                "{\"title\":\"  Complete Blood Count \",\"providerName\":\"\",\"docDate\":\"2026-03-04\",\"docType\":\"LAB_REPORT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Complete Blood Count"))
                .andExpect(jsonPath("$.providerName").doesNotExist())
                .andExpect(jsonPath("$.docDate").value("2026-03-04"))
                .andExpect(jsonPath("$.docType").value("LAB_REPORT"))
                .andExpect(jsonPath("$.userEdited").value(true));

        assertThat(labRepository.findByIdAndUserId(labId, user.getId()).orElseThrow().getObservedOn())
                .isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(medicationRepository.findByIdAndUserId(medicationId, user.getId()).orElseThrow().getPrescribedOn())
                .isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    void anEmptyCorrectionChangesNothing() throws Exception {
        send(patch("/api/v1/documents/" + documentId), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("CBC"))
                .andExpect(jsonPath("$.userEdited").value(false));
        send(patch("/api/v1/documents/" + documentId), "{\"title\":\"CBC\"}")
                .andExpect(jsonPath("$.userEdited").value(false));
    }

    @Test
    void impossibleDatesAreRejected() throws Exception {
        send(patch("/api/v1/documents/" + documentId), "{\"docDate\":\"" + LocalDate.now().plusDays(2) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.docDate").value("The record date can't be in the future"));
        send(patch("/api/v1/documents/" + documentId), "{\"docDate\":\"1899-12-31\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE"));
        assertThat(document().getDocDate()).isEqualTo(LocalDate.of(2026, 3, 14));
    }

    @Test
    void correctingALabValueRecomputesItsFlag() throws Exception {
        assertThat(labRepository.findByIdAndUserId(labId, user.getId()).orElseThrow().getFlag()).isEqualTo(LabFlag.LOW);

        send(patch("/api/v1/lab-results/" + labId), "{\"testName\":\"Haemoglobin\",\"valueText\":\"12.1\",\"unit\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testName").value("Haemoglobin"))
                .andExpect(jsonPath("$.valueText").value("12.1"))
                .andExpect(jsonPath("$.unit").doesNotExist())
                .andExpect(jsonPath("$.refText").value("13.0 - 17.0"))
                .andExpect(jsonPath("$.valueNumeric").value(12.1))
                .andExpect(jsonPath("$.flag").value("LOW"))
                .andExpect(jsonPath("$.userEdited").value(true));

        send(patch("/api/v1/lab-results/" + labId), "{\"valueText\":\"14.2\"}")
                .andExpect(jsonPath("$.flag").value("NORMAL"));
        send(patch("/api/v1/lab-results/" + labId), "{\"refText\":\"\"}")
                .andExpect(jsonPath("$.flag").doesNotExist())
                .andExpect(jsonPath("$.refLow").doesNotExist());
    }

    @Test
    void requiredResultFieldsCantBeCleared() throws Exception {
        send(patch("/api/v1/lab-results/" + labId), "{\"valueText\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.valueText").value("Result can't be empty"));
        send(patch("/api/v1/medications/" + medicationId), "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Name can't be empty"));
    }

    @Test
    void correctingAndRemovingMedicines() throws Exception {
        send(patch("/api/v1/medications/" + medicationId), "{\"name\":\"Metformin 500 mg\",\"route\":\"\",\"duration\":\"30 days\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Metformin 500 mg"))
                .andExpect(jsonPath("$.dosage").value("500 mg"))
                .andExpect(jsonPath("$.route").doesNotExist())
                .andExpect(jsonPath("$.duration").value("30 days"))
                .andExpect(jsonPath("$.userEdited").value(true));

        send(delete("/api/v1/medications/" + medicationId), "").andExpect(status().isNoContent());
        send(delete("/api/v1/lab-results/" + labId), "").andExpect(status().isNoContent());

        assertThat(medicationRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, user.getId())).isEmpty();
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, user.getId())).isEmpty();
        send(delete("/api/v1/lab-results/" + labId), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAB_RESULT_NOT_FOUND"));
    }
}
