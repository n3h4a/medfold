package com.medfold.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JWT settings. The app refuses to start without a signing secret of at least 32 characters. */
@Validated
@ConfigurationProperties(prefix = "medfold.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET must be set")
        @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
        String secret,
        @NotNull @DefaultValue("PT15M") Duration accessTokenTtl,
        @NotBlank @DefaultValue("medfold") String issuer) {
}
