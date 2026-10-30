package com.medfold.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProcessingJobTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final Duration DELAY = Duration.ofSeconds(30);

    private static ProcessingJob newJob(int maxAttempts) {
        return new ProcessingJob(UUID.randomUUID(), maxAttempts, NOW);
    }

    @Test
    void runsToCompletion() {
        ProcessingJob job = newJob(3);

        job.start(NOW);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getLockedAt()).isEqualTo(NOW);

        job.complete();
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getLockedAt()).isNull();
    }

    @Test
    void retryableFailureGoesBackToTheQueueAfterADelay() {
        ProcessingJob job = newJob(3);
        job.start(NOW);

        boolean willRetry = job.fail("AI service unavailable", true, NOW, DELAY);

        assertThat(willRetry).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getNextRunAt()).isEqualTo(NOW.plus(DELAY));
        assertThat(job.getLastError()).isEqualTo("AI service unavailable");
    }

    @Test
    void givesUpWhenAttemptsAreUsedUp() {
        ProcessingJob job = newJob(2);
        job.start(NOW);
        job.fail("timeout", true, NOW, DELAY);
        job.start(NOW.plus(DELAY));

        boolean willRetry = job.fail("timeout again", true, NOW.plus(DELAY), DELAY);

        assertThat(willRetry).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(2);
    }

    @Test
    void permanentFailureStopsImmediately() {
        ProcessingJob job = newJob(3);
        job.start(NOW);

        assertThat(job.fail("password-protected PDF", false, NOW, DELAY)).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void rejectsInvalidTransitionsAndSettings() {
        ProcessingJob job = newJob(1);

        assertThatThrownBy(job::complete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.fail("x", true, NOW, DELAY)).isInstanceOf(IllegalStateException.class);
        job.start(NOW);
        assertThatThrownBy(() -> job.start(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newJob(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void longErrorsAreTruncated() {
        ProcessingJob job = newJob(1);
        job.start(NOW);

        job.fail("e".repeat(10_000), false, NOW, DELAY);

        assertThat(job.getLastError()).hasSize(4000);
    }
}
