package com.medfold.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Refresh-token cookie settings. {@code cookieSecure} must stay true in production;
 * it is only turned off for plain-HTTP local development.
 */
@Validated
@ConfigurationProperties(prefix = "medfold.refresh-token")
public record RefreshTokenProperties(
        @NotNull @DefaultValue("P7D") Duration ttl,
        @NotBlank @DefaultValue("medfold_refresh") String cookieName,
        @DefaultValue("true") boolean cookieSecure) {

    /** The cookie is only sent to auth endpoints, never to the rest of the API. */
    public static final String COOKIE_PATH = "/api/v1/auth";
}
