package com.medfold.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.records.LabResult;
import com.medfold.records.LabResultRepository;
import com.medfold.records.Medication;
import com.medfold.records.MedicationRepository;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

/**
 * Guards the core privacy promise: one user can never see or change another user's data.
 *
 * The endpoint list is read from Spring's own route table, not written by hand. Every /api/v1
 * endpoint must be classified in {@link #ENDPOINTS}; adding an endpoint without classifying it
 * fails {@link #everyApiEndpointIsClassified()}. Every endpoint classified as OWNED_BY_ID is then
 * automatically attacked by a second user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserIsolationTest {

    enum Access {
        /** No token needed (auth flows, health). */
        PUBLIC,
        /** Acts on the caller's own account, identified only by the token. */
        SELF,
        /** Lists or creates resources; must only ever show or create the caller's own. */
        COLLECTION,
        /** Addresses one resource by id; must return 404 for anyone but its owner. */
        OWNED_BY_ID
    }

    private static final Map<String, Access> ENDPOINTS = Map.ofEntries(
            Map.entry("GET /api/v1/health", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/register", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/login", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/refresh", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/logout", Access.PUBLIC),
            Map.entry("GET /api/v1/auth/me", Access.SELF),
            Map.entry("GET /api/v1/documents", Access.COLLECTION),
            Map.entry("POST /api/v1/documents", Access.COLLECTION),
            Map.entry("GET /api/v1/documents/{id}", Access.OWNED_BY_ID),
            Map.entry("GET /api/v1/documents/{id}/status", Access.OWNED_BY_ID),
            Map.entry("GET /api/v1/documents/{id}/file", Access.OWNED_BY_ID),
            Map.entry("GET /api/v1/documents/{id}/results", Access.OWNED_BY_ID),
            Map.entry("POST /api/v1/documents/{id}/reprocess", Access.OWNED_BY_ID),
            Map.entry("DELETE /api/v1/documents/{id}", Access.OWNED_BY_ID),
            Map.entry("PATCH /api/v1/documents/{id}", Access.OWNED_BY_ID),
            Map.entry("PATCH /api/v1/lab-results/{id}", Access.OWNED_BY_ID),
            Map.entry("DELETE /api/v1/lab-results/{id}", Access.OWNED_BY_ID),
            Map.entry("PATCH /api/v1/medications/{id}", Access.OWNED_BY_ID),
            Map.entry("DELETE /api/v1/medications/{id}", Access.OWNED_BY_ID),
            Map.entry("GET /api/v1/labs", Access.COLLECTION),
            Map.entry("GET /api/v1/labs/{testKey}", Access.COLLECTION),
            Map.entry("GET /api/v1/medications", Access.COLLECTION),
            Map.entry("GET /api/v1/search", Access.COLLECTION));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alice;
    private String aliceToken;
    private User bob;
    private String bobToken;
    private byte[] alicesFileBytes;
    private UUID alicesDocumentId;
    private UUID alicesLabResultId;
    private UUID alicesMedicationId;

    @Autowired
    private LabResultRepository labResultRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @BeforeEach
    void setUp() throws Exception {
        alice = userRepository.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "h", "Alice"));
        bob = userRepository.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "h", "Bob"));
        aliceToken = jwtService.issueAccessToken(alice).value();
        bobToken = jwtService.issueAccessToken(bob).value();

        alicesFileBytes = ("%PDF-1.4\n% alice private " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
        alicesDocumentId = upload(aliceToken, alicesFileBytes);
        alicesLabResultId = labResultRepository.save(new LabResult(alicesDocumentId, alice.getId(), 0, "HbA1c", "hba1c",
                "6.1", "%", "4.0 - 5.6", 1, null)).getId();
        alicesMedicationId = medicationRepository.saveAll(List.of(new Medication(alicesDocumentId, alice.getId(), 0,
                "Metformin 500 mg", null, null, null, null, null, null))).getFirst().getId();
    }

    /** All /api/v1 routes as "METHOD /path/{var}". */
    private Set<String> apiRoutes() {
        Set<String> routes = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/")) {
                    continue;
                }
                Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
                assertThat(methods).as("%s must declare its HTTP method explicitly", pattern).isNotEmpty();
                methods.forEach(method -> routes.add(method.name() + " " + pattern));
            }
        });
        return routes;
    }

    private Set<String> routesWith(Access access) {
        Set<String> routes = new TreeSet<>();
        ENDPOINTS.forEach((route, a) -> {
            if (a == access) {
                routes.add(route);
            }
        });
        return routes;
    }

    /** Which of Alice's resources to attack for an OWNED_BY_ID route. New resource types must be added here. */
    private UUID alicesResourceFor(String route) {
        if (route.contains("/api/v1/documents/{id}")) {
            return alicesDocumentId;
        }
        if (route.contains("/api/v1/lab-results/{id}")) {
            return alicesLabResultId;
        }
        if (route.contains("/api/v1/medications/{id}")) {
            return alicesMedicationId;
        }
        throw new AssertionError("No owned test resource for " + route
                + ". Add one in UserIsolationTest.alicesResourceFor so its isolation is tested.");
    }

    private static AbstractMockHttpServletRequestBuilder<?> requestFor(String route, UUID id) {
        String[] parts = route.split(" ", 2);
        HttpMethod method = HttpMethod.valueOf(parts[0]);
        String uri = parts[1].replaceAll("\\{[^}]+}", id.toString());
        AbstractMockHttpServletRequestBuilder<?> builder = request(method, uri);
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return builder;
    }

    private UUID upload(String token, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", bytes))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private Map<String, Object> alicesDocumentRow() {
        return new TreeMap<>(jdbcTemplate.queryForMap("SELECT * FROM documents WHERE id = ?", alicesDocumentId));
    }

    private List<Map<String, Object>> alicesResultRows() {
        return List.of(
                new TreeMap<>(jdbcTemplate.queryForMap("SELECT * FROM lab_results WHERE id = ?", alicesLabResultId)),
                new TreeMap<>(jdbcTemplate.queryForMap("SELECT * FROM medications WHERE id = ?", alicesMedicationId)));
    }

    @Test
    void everyApiEndpointIsClassified() {
        Set<String> actual = apiRoutes();

        assertThat(actual)
                .as("Unclassified endpoints: add them to UserIsolationTest.ENDPOINTS so their isolation is tested")
                .isSubsetOf(ENDPOINTS.keySet());
        assertThat(ENDPOINTS.keySet())
                .as("Classified endpoints that no longer exist: remove them from UserIsolationTest.ENDPOINTS")
                .isSubsetOf(actual);
    }

    @Test
    void anotherUserGetsNotFoundOnEveryOwnedEndpointAndChangesNothing() throws Exception {
        Map<String, Object> before = alicesDocumentRow();
        List<Map<String, Object>> resultsBefore = alicesResultRows();

        for (String route : routesWith(Access.OWNED_BY_ID)) {
            UUID target = alicesResourceFor(route);
            MvcResult result = mockMvc.perform(requestFor(route, target).header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).as("Bob calling %s on Alice's resource", route).isEqualTo(404);
            assertThat(result.getResponse().getContentAsString()).as("%s response body", route)
                    .contains("_NOT_FOUND")
                    .doesNotContain(alice.getEmail(), "alice private");
        }

        assertThat(alicesDocumentRow()).as("Alice's document row after Bob's attempts").isEqualTo(before);
        assertThat(alicesResultRows()).as("Alice's lab result and medicine after Bob's attempts").isEqualTo(resultsBefore);
        mockMvc.perform(get("/api/v1/documents/" + alicesDocumentId + "/file")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(alicesFileBytes));
    }

    @Test
    void ownerCanUseEveryOwnedEndpoint() throws Exception {
        for (String route : routesWith(Access.OWNED_BY_ID)) {
            if (route.startsWith("DELETE ")) {
                continue; // checked last, below
            }
            mockMvc.perform(requestFor(route, alicesResourceFor(route)).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).as("Alice calling %s", route).isBetween(200, 299));
        }
        // Deleting the document cascades to its results, so it goes last.
        List<String> deletes = routesWith(Access.OWNED_BY_ID).stream().filter(r -> r.startsWith("DELETE "))
                .sorted(Comparator.comparing((String r) -> r.contains("/documents/")))
                .toList();
        for (String route : deletes) {
            mockMvc.perform(requestFor(route, alicesResourceFor(route)).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void collectionsOnlyShowTheCallersOwnData() throws Exception {
        UUID bobsDocumentId = upload(bobToken, ("%PDF-1.4\n% bob " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bobsDocumentId.toString()));
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(alicesDocumentId.toString()));

        String bobsOwner = jdbcTemplate.queryForObject("SELECT user_id::text FROM documents WHERE id = ?", String.class, bobsDocumentId);
        String bobsKey = jdbcTemplate.queryForObject("SELECT storage_key FROM documents WHERE id = ?", String.class, bobsDocumentId);
        assertThat(bobsOwner).isEqualTo(bob.getId().toString());
        assertThat(bobsKey).startsWith(bob.getId() + "/");
    }

    @Test
    void sameFileUploadedByBothUsersStaysSeparate() throws Exception {
        UUID bobsCopy = upload(bobToken, alicesFileBytes);

        assertThat(bobsCopy).isNotEqualTo(alicesDocumentId);
        mockMvc.perform(request(HttpMethod.DELETE, "/api/v1/documents/" + bobsCopy)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/" + alicesDocumentId + "/file")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk());
    }

    @Test
    void selfEndpointsReturnOnlyTheCaller() throws Exception {
        for (String route : routesWith(Access.SELF)) {
            mockMvc.perform(requestFor(route, UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .as(route).contains(bob.getEmail()).doesNotContain(alice.getEmail()));
        }
    }

    @Test
    void everyNonPublicEndpointRejectsAnonymousCalls() throws Exception {
        for (String route : apiRoutes()) {
            if (ENDPOINTS.get(route) == Access.PUBLIC) {
                continue;
            }
            mockMvc.perform(requestFor(route, alicesDocumentId))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).as("anonymous %s", route).isEqualTo(401));
        }
        assertThat(alicesDocumentRow()).containsEntry("id", alicesDocumentId);
    }
}
