package com.medfold.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AiServiceHealthTest {

    @Test
    void remembersTheLastAnswerForTenSeconds() {
        AiServiceClient client = mock(AiServiceClient.class);
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-30T09:00:00Z");
        when(clock.instant()).thenReturn(start, start.plusSeconds(9), start.plusSeconds(10));
        when(client.availability()).thenReturn(AiServiceClient.Availability.UP, AiServiceClient.Availability.DOWN);
        AiServiceHealth health = new AiServiceHealth(client, clock);

        assertThat(health.availability()).isEqualTo(AiServiceClient.Availability.UP);
        assertThat(health.availability()).as("cached").isEqualTo(AiServiceClient.Availability.UP);
        assertThat(health.availability()).as("checked again").isEqualTo(AiServiceClient.Availability.DOWN);
        verify(client, times(2)).availability();
    }
}
