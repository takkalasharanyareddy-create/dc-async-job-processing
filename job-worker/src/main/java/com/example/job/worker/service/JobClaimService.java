package com.example.job.worker.service;

import com.example.job.common.entity.Job;
import com.example.job.common.enums.JobStatus;
import com.example.job.common.repository.JobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobClaimService {

    private final JobRepository jobRepository;

    public JobClaimService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Atomically claims one PENDING job and marks it RUNNING (increments attempt_count).
     */
    @Transactional
    public Optional<UUID> claimNextJobId() {
        Optional<Job> pending = jobRepository.findNextPendingForUpdate();
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        Job job = pending.get();
        Instant now = Instant.now();
        int updated = jobRepository.markRunning(
                job.getId(), JobStatus.RUNNING, JobStatus.PENDING, now, now);
        if (updated == 0) {
            return Optional.empty();
        }
        return Optional.of(job.getId());
    }
}
