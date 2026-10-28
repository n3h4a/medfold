package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String email, String password, String fullName) throws Exception {
        String name = fullName == null ? "null" : "\"" + fullName + "\"";
        return postJson("/api/v1/auth/register",
                "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":%s}".formatted(email, password, name));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    @Test
    void registerCreatesUserAndReturnsAccessToken() throws Exception {
        String email = uniqueEmail();

        String body = register(email.toUpperCase(), PASSWORD, "  Asha Rao ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        User saved = userRepository.findByEmailNormalized(email).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);

        String token = JsonPath.read(body, "$.accessToken");
        assertThat(jwtDecoder.decode(token).getSubject()).isEqualTo(saved.getId().toString());
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, null).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        register("not-an-email", "short", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.fields.password").value("Password must be 8 to 72 characters"));
    }

    @Test
    void registerRejectsPasswordOverBcryptByteLimit() throws Exception {
        // 30 three-byte characters: within 72 characters but 90 bytes.
        register(uniqueEmail(), "€".repeat(30), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void registerRejectsMalformedJson() throws Exception {
        postJson("/api/v1/auth/register", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginReturnsTokenForCorrectCredentials() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Asha").andExpect(status().isCreated());

        String body = login(" " + email.toUpperCase() + " ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        assertThat(jwtDecoder.decode(token).getClaimAsString("email")).isEqualTo(email);
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailTheSameWay() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, null).andExpect(status().isCreated());

        login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));

        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        postJson("/api/v1/auth/login", "{\"email\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void otherEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
}
