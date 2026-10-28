package com.medfold.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medfold.support.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshFlowTest {

    private static final String COOKIE = "medfold_refresh";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    private String registerAndGetRefreshToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return refreshCookieValue(result);
    }

    private static String refreshCookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(COOKIE + "=");
        return header.substring(COOKIE.length() + 1, header.indexOf(';'));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, token)));
    }

    private static String uniqueEmail() {
        return "refresh-" + UUID.randomUUID() + "@example.com";
    }

    private int activeTokenCount(String email) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                WHERE u.email = ? AND t.revoked_at IS NULL""", Integer.class, email);
    }

    @Test
    void registerAndLoginSetSecureHttpOnlyCookieStoredOnlyAsHash() throws Exception {
        String email = uniqueEmail();
        String raw = registerAndGetRefreshToken(email);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String header = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800");
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
        Integer storedRaw = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, raw);
        Integer storedHash = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, RefreshTokenService.hash(raw));
        assertThat(storedRaw).isZero();
        assertThat(storedHash).isOne();
        assertThat(activeTokenCount(email)).isEqualTo(2);
    }

    @Test
    void refreshRotatesTokenAndReturnsNewAccessToken() throws Exception {
        String email = uniqueEmail();
        String first = registerAndGetRefreshToken(email);

        MvcResult result = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();
        String second = refreshCookieValue(result);
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        assertThat(second).isNotEqualTo(first);
        assertThat(jwtDecoder.decode(accessToken).getClaimAsString("email")).isEqualTo(email);
        assertThat(activeTokenCount(email)).isOne();
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedTokenRevokesAllSessionsOfThatUser() throws Exception {
        String email = uniqueEmail();
        String first = registerAndGetRefreshToken(email);
        String second = refreshCookieValue(refresh(first).andExpect(status().isOk()).andReturn());

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        assertThat(activeTokenCount(email)).isZero();
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void failedRefreshClearsTheCookie() throws Exception {
        MvcResult result = refresh("unknown-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;").contains("Max-Age=0");
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        String raw = registerAndGetRefreshToken(email);
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), RefreshTokenService.hash(raw));

        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTokenAndClearsCookie() throws Exception {
        String email = uniqueEmail();
        String raw = registerAndGetRefreshToken(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, raw)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(activeTokenCount(email)).isZero();
        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookieStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }
}
