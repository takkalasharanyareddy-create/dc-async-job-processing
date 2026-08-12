package com.example.job.worker.service;

import com.example.job.common.entity.Job;
import com.example.job.worker.config.WorkerProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobLifecycleService jobLifecycleService;
    private final WorkerProperties workerProperties;

    public JobExecutionService(JobLifecycleService jobLifecycleService, WorkerProperties workerProperties) {
        this.jobLifecycleService = jobLifecycleService;
        this.workerProperties = workerProperties;
    }

    public void process(UUID jobId) {
        Job job = jobLifecycleService.requireJob(jobId);
        if (job == null) {
            log.warn("Claimed job {} no longer exists", jobId);
            return;
        }

        int maxAttempts = job.getMaxAttempts() > 0
                ? job.getMaxAttempts()
                : workerProperties.getMaxAttempts();

        log.info("Processing job {} (attempt {}/{})", jobId, job.getAttemptCount(), maxAttempts);

        try {
            doWork(job);
            jobLifecycleService.markCompleted(jobId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jobLifecycleService.markFailedOrRetry(jobId, "Interrupted while processing");
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            jobLifecycleService.markFailedOrRetry(jobId, message);
        }
    }

    private void doWork(Job job) throws InterruptedException {
        Thread.sleep(workerProperties.getProcessingDelayMs());
        if (job.getPayload() != null && job.getPayload().contains("forceFail")) {
            throw new IllegalStateException("Forced failure for retry/DLQ testing");
        }
    }
}
