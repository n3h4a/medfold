package com.medfold.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.document.DocumentPage;
import com.medfold.document.DocumentPageRepository;
import com.medfold.document.TextSource;
import com.medfold.records.LabResult;
import com.medfold.records.LabResultRepository;
import com.medfold.records.Medication;
import com.medfold.records.MedicationRepository;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SearchApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentPageRepository pageRepository;
    @Autowired private LabResultRepository labRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;
    private UUID cbc;
    private UUID prescription;

    @BeforeEach
    void setUp() throws Exception {
        user = newUser();
        token = jwtService.issueAccessToken(user).value();
        cbc = upload(token, "cbc.pdf");
        jdbcTemplate.update("UPDATE documents SET title = 'Complete Blood Count', facility_name = 'City Diagnostics', "
                + "doc_date = '2026-03-14' WHERE id = ?", cbc);
        pageRepository.saveAll(List.of(new DocumentPage(cbc, user.getId(), 2, "Patient: A. Rao\nHaemoglobin    12.1 g/dL"
                + "   13.0 - 17.0\nPlatelet count 2.5 lakh/cumm. Sample collected at 9:30 AM at the fasting_glucose desk. 100% done",
                TextSource.PDF_TEXT, null, 120)));
        labRepository.save(new LabResult(cbc, user.getId(), 0, "Haemoglobin", "hemoglobin", "12.1", "g/dL", null, 2, null));

        prescription = upload(token, "rx.jpg.pdf");
        jdbcTemplate.update("UPDATE documents SET title = 'Prescription', provider_name = 'Dr. Mehta', doc_date = '2025-01-02' WHERE id = ?",
                prescription);
        medicationRepository.saveAll(List.of(new Medication(prescription, user.getId(), 0, "Metformin 500 mg", null, null,
                null, null, null, null)));
    }

    private User newUser() {
        return userRepository.saveAndFlush(new User("search-" + UUID.randomUUID() + "@example.com", "h", null));
    }

    private UUID upload(String asToken, String name) throws Exception {
        byte[] pdf = ("%PDF-1.4\n% search " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String body = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", name, "application/pdf", pdf))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + asToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions search(String q) throws Exception {
        return mockMvc.perform(get("/api/v1/search").param("q", q).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private List<String> ids(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$[*].document.id");
    }

    @Test
    void findsWordsInPageTextWithASnippetAndThePage() throws Exception {
        search("haemoglobin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].document.id").value(cbc.toString()))
                .andExpect(jsonPath("$[0].document.title").value("Complete Blood Count"))
                .andExpect(jsonPath("$[0].pageNumber").value(2))
                .andExpect(jsonPath("$[0].snippet").value(startsWith("Patient: A. Rao Haemoglobin 12.1 g/dL")));
    }

    @Test
    void findsDocumentDetailsTestsAndMedicinesByPartOfAWord() throws Exception {
        assertThat(ids(search("diagnost"))).containsExactly(cbc.toString());
        assertThat(ids(search("MEHTA"))).containsExactly(prescription.toString());
        assertThat(ids(search("metfor"))).containsExactly(prescription.toString());
        assertThat(ids(search("pdf"))).as("filenames, newest record first").containsExactly(cbc.toString(), prescription.toString());
        search("metfor").andExpect(jsonPath("$[0].snippet").doesNotExist());
    }

    @Test
    void everyWordMustMatchSomewhereInTheSameDocument() throws Exception {
        assertThat(ids(search("platelet city"))).containsExactly(cbc.toString());
        assertThat(ids(search("platelet mehta"))).isEmpty();
    }

    @Test
    void wildcardCharactersAreSearchedLiterally() throws Exception {
        assertThat(ids(search("100%"))).containsExactly(cbc.toString());
        assertThat(ids(search("%"))).containsExactly(cbc.toString());
        assertThat(ids(search("fasting_g"))).containsExactly(cbc.toString());
        assertThat(ids(search("g_x"))).isEmpty();
    }

    @Test
    void neverReturnsAnotherUsersDocuments() throws Exception {
        User other = newUser();
        String otherToken = jwtService.issueAccessToken(other).value();
        UUID theirs = upload(otherToken, "theirs.pdf");
        pageRepository.saveAll(List.of(new DocumentPage(theirs, other.getId(), 1, "Haemoglobin 9.0", TextSource.PDF_TEXT, null, 15)));
        jdbcTemplate.update("UPDATE documents SET title = 'Complete Blood Count' WHERE id = ?", theirs);

        assertThat(ids(search("haemoglobin"))).containsExactly(cbc.toString());
        assertThat(ids(search("blood count"))).containsExactly(cbc.toString());
    }

    @Test
    void misspellingsFindTheDocumentAndSayWhatMatched() throws Exception {
        search("haemoglobn")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].document.id").value(cbc.toString()))
                .andExpect(jsonPath("$[0].exact").value(false))
                .andExpect(jsonPath("$[0].highlights[0]").value("Haemoglobin"))
                .andExpect(jsonPath("$[0].pageNumber").value(2))
                .andExpect(jsonPath("$[0].snippet").value(containsString("Haemoglobin 12.1 g/dL")));
        assertThat(ids(search("platlet"))).containsExactly(cbc.toString());
        assertThat(ids(search("metformn"))).as("medicine names").containsExactly(prescription.toString());
        assertThat(ids(search("diagnostcs"))).as("document details").containsExactly(cbc.toString());
        search("haemoglobin").andExpect(jsonPath("$[0].exact").value(true)).andExpect(jsonPath("$[0].highlights[0]").value("Haemoglobin"));
    }

    @Test
    void exactMatchesRankAboveSimilarSpellingsEvenWhenOlder() throws Exception {
        UUID newerTypo = upload(token, "typo.pdf");
        jdbcTemplate.update("UPDATE documents SET title = 'Old scan', doc_date = '2026-09-01' WHERE id = ?", newerTypo);
        pageRepository.saveAll(List.of(new DocumentPage(newerTypo, user.getId(), 1, "Haemoglobn 11.0 g/dL (OCR misread)",
                TextSource.TESSERACT, 0.7f, 30)));

        search("haemoglobin")
                .andExpect(jsonPath("$[0].document.id").value(cbc.toString()))
                .andExpect(jsonPath("$[0].exact").value(true))
                .andExpect(jsonPath("$[1].document.id").value(newerTypo.toString()))
                .andExpect(jsonPath("$[1].exact").value(false))
                .andExpect(jsonPath("$[1].highlights[0]").value("Haemoglobn"));
    }

    @Test
    void similarSpellingsMatchWholeWordsOnly() throws Exception {
        UUID footer = upload(token, "footer.pdf");
        pageRepository.saveAll(List.of(new DocumentPage(footer, user.getId(), 1,
                "Synthetic demo document, invented for Medfold. Type 2 diabetes mellitus.", TextSource.PDF_TEXT, null, 70)));

        assertThat(ids(search("metfor"))).as("not across \"for Medfold\"").containsExactly(prescription.toString());
        // Strict similarity 0.50: not above pg_trgm's default threshold (0.5), so this needs Medfold's own (0.45).
        assertThat(ids(search("diabetic"))).containsExactly(footer.toString());
    }

    @Test
    void shortAndUnrelatedWordsOnlyMatchAsTyped() throws Exception {
        assertThat(ids(search("cty"))).as("three letters: no similar spellings").isEmpty();
        assertThat(ids(search("zebra"))).isEmpty();
        assertThat(ids(search("thyroid"))).isEmpty();
        assertThat(ids(search("haemoglobn mehta"))).as("every word must still match the same document").isEmpty();
    }

    @Test
    void similarSpellingsNeverReachAnotherUsersDocuments() throws Exception {
        User other = newUser();
        String otherToken = jwtService.issueAccessToken(other).value();
        UUID theirs = upload(otherToken, "theirs.pdf");
        pageRepository.saveAll(List.of(new DocumentPage(theirs, other.getId(), 1, "Platelet count 1.9 Thyroxine", TextSource.PDF_TEXT, null, 30)));

        assertThat(ids(search("platlet"))).containsExactly(cbc.toString());
        assertThat(ids(search("thyroxin"))).isEmpty();
    }

    @Test
    void everyWordIsHighlightedInTheSnippet() throws Exception {
        search("haemoglobn platelet").andExpect(jsonPath("$[0].highlights[0]").value("Haemoglobin"))
                .andExpect(jsonPath("$[0].highlights[1]").value("Platelet"));
        assertThat(SearchService.highlights("Total Cholesterol 181 mg/dL", List.of("total", "cholestrol", "zebra", "mg")))
                .containsExactly("Total", "Cholesterol", "mg");
    }

    @Test
    void snippetsPointAtTheClosestWord() {
        SearchService.Snippet s = SearchService.snippet("Sample: Total Cholesterol 181 mg/dL", "cholestrol");
        assertThat(s.highlight()).isEqualTo("Cholesterol");
        assertThat(s.text()).isEqualTo("Sample: Total Cholesterol 181 mg/dL");
        assertThat(SearchService.snippet("Fasting glucose 92", "fasting").highlight()).isEqualTo("Fasting");
        assertThat(SearchService.similarity("platelet", "platelet")).isEqualTo(1.0);
        assertThat(SearchService.similarity("platelet", "zebra")).isZero();
    }

    @Test
    void blankQueriesReturnNothingAndOverlongOnesAreRejected() throws Exception {
        search("   ").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        search("x".repeat(101)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUERY_TOO_LONG"));
    }
}
