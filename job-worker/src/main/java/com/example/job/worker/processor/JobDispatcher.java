package com.example.job.worker.processor;

import com.example.job.worker.config.WorkerProperties;
import com.example.job.worker.service.JobClaimService;
import com.example.job.worker.service.JobExecutionService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps up to {@code worker.pool-size} jobs in flight.
 * When one finishes, the next PENDING job is claimed immediately.
 */
@Component
public class JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);

    private final JobClaimService jobClaimService;
    private final JobExecutionService jobExecutionService;
    private final Executor jobTaskExecutor;
    private final WorkerProperties workerProperties;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final ReentrantLock fillLock = new ReentrantLock();

    public JobDispatcher(
            JobClaimService jobClaimService,
            JobExecutionService jobExecutionService,
            @Qualifier("jobTaskExecutor") Executor jobTaskExecutor,
            WorkerProperties workerProperties) {
        this.jobClaimService = jobClaimService;
        this.jobExecutionService = jobExecutionService;
        this.jobTaskExecutor = jobTaskExecutor;
        this.workerProperties = workerProperties;
    }

    @Scheduled(fixedDelayString = "${worker.poll-interval-ms:500}")
    public void pollAndFill() {
        fillSlots();
    }

    private void fillSlots() {
        if (!fillLock.tryLock()) {
            return;
        }
        try {
            int poolSize = workerProperties.getPoolSize();
            while (inFlight.get() < poolSize) {
                Optional<UUID> claimed = jobClaimService.claimNextJobId();
                if (claimed.isEmpty()) {
                    break;
                }

                UUID jobId = claimed.get();
                inFlight.incrementAndGet();
                log.debug("Dispatched job {} (inFlight={}/{})", jobId, inFlight.get(), poolSize);

                try {
                    jobTaskExecutor.execute(() -> runJob(jobId));
                } catch (RuntimeException ex) {
                    inFlight.decrementAndGet();
                    log.error("Failed to submit job {} to executor: {}", jobId, ex.getMessage());
                    break;
                }
            }
        } finally {
            fillLock.unlock();
        }
    }

    private void runJob(UUID jobId) {
        try {
            jobExecutionService.process(jobId);
        } finally {
            inFlight.decrementAndGet();
            // Immediately try to keep the pool full
            fillSlots();
        }
    }
}
