package com.medfold.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Connection to the internal AI service.
 *
 * @param baseUrl        e.g. http://localhost:8000
 * @param internalKey    shared secret sent as X-Internal-Api-Key; calls fail (and are retried) while it is empty
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout    how long one call may take; OCR of a long scan and an LLM call with a retry are slow
 */
@Validated
@ConfigurationProperties(prefix = "medfold.ai")
public record AiServiceProperties(
        @NotBlank @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("") String internalKey,
        @NotNull @DefaultValue("PT5S") Duration connectTimeout,
        @NotNull @DefaultValue("PT5M") Duration readTimeout) {
}
