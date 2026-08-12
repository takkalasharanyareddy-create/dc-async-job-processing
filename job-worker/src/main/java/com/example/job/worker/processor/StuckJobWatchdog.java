package com.example.job.worker.processor;

import com.example.job.worker.service.JobLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves jobs stuck in RUNNING longer than {@code worker.running-timeout-ms}
 * straight to DEAD + dead_letter_jobs (no retries).
 */
@Component
public class StuckJobWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StuckJobWatchdog.class);

    private final JobLifecycleService jobLifecycleService;

    public StuckJobWatchdog(JobLifecycleService jobLifecycleService) {
        this.jobLifecycleService = jobLifecycleService;
    }

    @Scheduled(fixedDelayString = "${worker.timeout-check-interval-ms:60000}")
    public void sweepTimedOutJobs() {
        int moved = jobLifecycleService.sweepTimedOutRunningJobs();
        if (moved > 0) {
            log.warn("Stuck-job watchdog moved {} RUNNING job(s) to DEAD/DLQ", moved);
        }
    }
}
