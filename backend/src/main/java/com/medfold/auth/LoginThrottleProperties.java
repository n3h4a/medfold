package com.medfold.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Limits on wrong-password attempts within a sliding window.
 *
 * @param maxFailuresPerAccount per email address (or account, for confirmations)
 * @param maxFailuresPerAddress per client IP, to slow down guessing across many accounts
 */
@Validated
@ConfigurationProperties(prefix = "medfold.login-throttle")
public record LoginThrottleProperties(
        @Min(1) @DefaultValue("5") int maxFailuresPerAccount,
        @Min(1) @DefaultValue("30") int maxFailuresPerAddress,
        @NotNull @DefaultValue("PT15M") Duration window) {
}
