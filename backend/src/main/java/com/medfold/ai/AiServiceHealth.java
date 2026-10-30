package com.medfold.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * The AI service's availability for the public health check, remembered for a few seconds so
 * frequent status polling never turns into a stream of calls to the AI service.
 */
@Component
public class AiServiceHealth {

    static final Duration CACHE_FOR = Duration.ofSeconds(10);

    private final AiServiceClient client;
    private final Clock clock;
    private AiServiceClient.Availability last;
    private Instant checkedAt = Instant.MIN;

    public AiServiceHealth(AiServiceClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public synchronized AiServiceClient.Availability availability() {
        Instant now = clock.instant();
        if (last == null || !now.isBefore(checkedAt.plus(CACHE_FOR))) {
            last = client.availability();
            checkedAt = now;
        }
        return last;
    }
}
