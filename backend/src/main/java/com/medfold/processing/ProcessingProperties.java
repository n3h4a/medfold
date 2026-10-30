package com.medfold.processing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Background processing settings.
 *
 * @param enabled     run the worker in this instance
 * @param batchSize   jobs claimed per poll
 * @param maxAttempts tries per document before it is marked FAILED
 * @param retryDelay  wait before the first retry; doubles on each further retry (capped at one hour)
 * @param staleAfter  a RUNNING job untouched this long is treated as abandoned by a crashed worker
 */
@Validated
@ConfigurationProperties(prefix = "medfold.processing")
public record ProcessingProperties(
        @DefaultValue("false") boolean enabled,
        @NotNull @DefaultValue("PT2S") Duration pollInterval,
        @Min(1) @Max(50) @DefaultValue("3") int batchSize,
        @Min(1) @Max(10) @DefaultValue("3") int maxAttempts,
        @NotNull @DefaultValue("PT30S") Duration retryDelay,
        @NotNull @DefaultValue("PT10M") Duration staleAfter) {
}
