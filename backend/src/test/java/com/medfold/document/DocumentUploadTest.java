package com.medfold.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.storage.StorageService;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private StorageService storageService;

    private User user;
    private String token;

    @BeforeEach
    void createUser() {
        user = userRepository.saveAndFlush(new User("upload-" + UUID.randomUUID() + "@example.com", "h", null));
        token = jwtService.issueAccessToken(user).value();
    }

    private ResultActions upload(String token, MockMultipartFile file) throws Exception {
        var request = multipart("/api/v1/documents").file(file);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private static MockMultipartFile file(String name, String declaredType, byte[] content) {
        return new MockMultipartFile("file", name, declaredType, content);
    }

    private static String uniquePdfBody() {
        return "unique " + UUID.randomUUID();
    }

    private long userFileCount() throws IOException {
        Path dir = Path.of("target/test-uploads", user.getId().toString());
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void uploadsPdfAndStoresFileAndRow() throws Exception {
        byte[] content = TestFiles.pdf(uniquePdfBody());

        String body = upload(token, file("cbc-march.pdf", "application/pdf", content))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/documents/")))
                .andExpect(jsonPath("$.originalFilename").value("cbc-march.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        Document saved = documentRepository.findByIdAndUserId(id, user.getId()).orElseThrow();
        assertThat(saved.getStorageKey()).startsWith(user.getId() + "/").endsWith(".pdf");
        assertThat(saved.getSha256()).hasSize(64);
        assertThat(storageService.load(saved.getStorageKey()).getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void usesContentNotNameOrDeclaredTypeToDetectType() throws Exception {
        upload(token, file("scan.bin", "application/octet-stream", TestFiles.png()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/png"));

        upload(token, file("photo.png", "image/png", TestFiles.jpeg()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"));
    }

    @Test
    void rejectsFilesThatOnlyPretendToBeSupported() throws Exception {
        upload(token, file("report.pdf", "application/pdf", "MZ\u0090 not really a pdf".getBytes()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        assertThat(userFileCount()).isZero();
    }

    @Test
    void rejectsEmptyAndMissingFiles() throws Exception {
        upload(token, file("empty.pdf", "application/pdf", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));

        mockMvc.perform(multipart("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void rejectsFilesOverTenMegabytes() throws Exception {
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(TestFiles.pdf(""), 0, tooBig, 0, 8);

        upload(token, file("huge.pdf", "application/pdf", tooBig))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        assertThat(userFileCount()).isZero();
    }

    @Test
    void rejectsDuplicateForSameUserAndPointsToExistingDocument() throws Exception {
        byte[] content = TestFiles.pdf(uniquePdfBody());
        String first = upload(token, file("a.pdf", "application/pdf", content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        upload(token, file("renamed-copy.pdf", "application/pdf", content))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"))
                .andExpect(jsonPath("$.details.existingDocumentId").value((String) JsonPath.read(first, "$.id")));

        assertThat(userFileCount()).isEqualTo(1);
    }

    @Test
    void anotherUserCanUploadTheSameFile() throws Exception {
        byte[] content = TestFiles.pdf(uniquePdfBody());
        upload(token, file("a.pdf", "application/pdf", content)).andExpect(status().isCreated());

        User other = userRepository.saveAndFlush(new User("other-" + UUID.randomUUID() + "@example.com", "h", null));
        upload(jwtService.issueAccessToken(other).value(), file("a.pdf", "application/pdf", content))
                .andExpect(status().isCreated());
    }

    @Test
    void cleansClientSuppliedFilename() throws Exception {
        upload(token, file("C:\\Users\\asha\\Desktop\\lab report.pdf", "application/pdf", TestFiles.pdf(uniquePdfBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("lab report.pdf"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        upload(null, file("a.pdf", "application/pdf", TestFiles.pdf(uniquePdfBody())))
                .andExpect(status().isUnauthorized());
    }
}
