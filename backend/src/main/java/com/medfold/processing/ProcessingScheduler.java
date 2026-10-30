package com.medfold.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the queue on a fixed delay. Only exists when medfold.processing.enabled=true. */
@Component
@ConditionalOnProperty(prefix = "medfold.processing", name = "enabled", havingValue = "true")
public class ProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProcessingScheduler.class);

    private final JobRunner jobRunner;

    public ProcessingScheduler(JobRunner jobRunner) {
        this.jobRunner = jobRunner;
        log.info("Background document processing is enabled");
    }

    @Scheduled(fixedDelayString = "${medfold.processing.poll-interval:PT2S}", initialDelayString = "PT5S")
    public void poll() {
        try {
            int ran = jobRunner.runDue();
            if (ran > 0) {
                log.debug("Processed {} job(s)", ran);
            }
        } catch (RuntimeException ex) {
            // Never let one bad cycle stop the schedule (e.g. the database restarting).
            log.error("Processing cycle failed", ex);
        }
    }
}
