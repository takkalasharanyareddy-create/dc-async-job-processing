package com.example.job.worker.service;

import com.example.job.common.entity.DeadLetterJob;
import com.example.job.common.entity.Job;
import com.example.job.common.enums.JobStatus;
import com.example.job.common.repository.DeadLetterJobRepository;
import com.example.job.common.repository.JobRepository;
import com.example.job.worker.config.WorkerProperties;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(JobLifecycleService.class);

    private final JobRepository jobRepository;
    private final DeadLetterJobRepository deadLetterJobRepository;
    private final WorkerProperties workerProperties;

    public JobLifecycleService(
            JobRepository jobRepository,
            DeadLetterJobRepository deadLetterJobRepository,
            WorkerProperties workerProperties) {
        this.jobRepository = jobRepository;
        this.deadLetterJobRepository = deadLetterJobRepository;
        this.workerProperties = workerProperties;
    }

    @Transactional(readOnly = true)
    public Job requireJob(UUID jobId) {
        return jobRepository.findById(jobId).orElse(null);
    }

    @Transactional
    public void markCompleted(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus(JobStatus.COMPLETED);
        job.setResult("Processed payload: " + job.getPayload());
        job.setError(null);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        log.info("Completed job {}", jobId);
    }

    @Transactional
    public void markFailedOrRetry(UUID jobId, String message) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        int maxAttempts = job.getMaxAttempts() > 0
                ? job.getMaxAttempts()
                : workerProperties.getMaxAttempts();

        job.setError(message);

        if (job.getAttemptCount() < maxAttempts) {
            job.setStatus(JobStatus.PENDING);
            job.setStartedAt(null);
            job.setCompletedAt(null);
            jobRepository.save(job);
            log.warn("Job {} failed attempt {}/{} — requeued as PENDING. Error: {}",
                    jobId, job.getAttemptCount(), maxAttempts, message);
            return;
        }

        job.setStatus(JobStatus.DEAD);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        DeadLetterJob dlq = new DeadLetterJob();
        dlq.setOriginalJobId(job.getId());
        dlq.setName(job.getName());
        dlq.setPayload(job.getPayload());
        dlq.setError(message);
        dlq.setAttempts(job.getAttemptCount());
        dlq.setFailedAt(Instant.now());
        deadLetterJobRepository.save(dlq);

        log.error("Job {} moved to dead letter queue after {} attempts. Error: {}",
                jobId, job.getAttemptCount(), message);
    }
}
