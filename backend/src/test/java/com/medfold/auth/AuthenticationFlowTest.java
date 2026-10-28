package com.medfold.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    private User newUser() {
        return userRepository.saveAndFlush(
                new User("flow-" + UUID.randomUUID() + "@example.com", "hash", "Flow User"));
    }

    private String validTokenFor(User user) {
        return jwtService.issueAccessToken(user).value();
    }

    private static String sign(JwtEncoder encoder, String subject, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject).issuer(issuer).issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions getMe(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    void meReturnsCurrentUserForValidToken() throws Exception {
        User user = newUser();

        getMe(validTokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.fullName").value("Flow User"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void tokenFromLoginWorksEndToEnd() throws Exception {
        String email = "e2e-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"correct-horse-battery\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");

        getMe(token).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        expectInvalidToken(getMe("not-a-jwt"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String token = sign(jwtEncoder, newUser().getId().toString(), "medfold", past, past.plusSeconds(60));

        expectInvalidToken(getMe(token));
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = sign(jwtEncoder, newUser().getId().toString(), "someone-else", now, now.plusSeconds(600));

        expectInvalidToken(getMe(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("attacker-secret-0123456789abcdef01234567".getBytes(), "HmacSHA256")));
        Instant now = Instant.now();
        String token = sign(forger, newUser().getId().toString(), "medfold", now, now.plusSeconds(600));

        expectInvalidToken(getMe(token));
    }

    @Test
    void tokenForDeletedUserIsUnauthorized() throws Exception {
        User user = newUser();
        String token = validTokenFor(user);
        userRepository.delete(user);

        getMe(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void publicEndpointsIgnoreStaleTokens() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage")
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsStillRequireTokenWithSameAuthPrefix() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-admin"))
                .andExpect(status().isUnauthorized());
    }
}
