package com.medfold.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medfold.support.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginThrottleApiTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired private MockMvc mockMvc;

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void afterFiveWrongPasswordsEvenTheRightOneIsRefusedForAWhile() throws Exception {
        String email = "throttle-" + UUID.randomUUID() + "@example.com";
        register(email);
        for (int i = 0; i < 5; i++) {
            login(email, "guess " + i).andExpect(status().isUnauthorized());
        }

        login(email.toUpperCase(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").value("Too many failed attempts. Try again in 15 minutes"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").isNumber());

        String other = "throttle-" + UUID.randomUUID() + "@example.com";
        register(other);
        login(other, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailsAreLimitedTheSameWay() throws Exception {
        String nobody = "nobody-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            login(nobody, "guess").andExpect(status().isUnauthorized());
        }
        login(nobody, "guess").andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulLoginResetsTheCount() throws Exception {
        String email = "throttle-" + UUID.randomUUID() + "@example.com";
        register(email);
        for (int i = 0; i < 4; i++) {
            login(email, "guess").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "guess").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmingAccountDeletionIsLimitedToo() throws Exception {
        String token = register("throttle-" + UUID.randomUUID() + "@example.com");
        for (int i = 0; i < 5; i++) {
            deleteAccount(token, "guess").andExpect(status().isForbidden());
        }
        deleteAccount(token, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    private ResultActions deleteAccount(String token, String password) throws Exception {
        return mockMvc.perform(delete("/api/v1/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"%s\"}".formatted(password)));
    }
}
