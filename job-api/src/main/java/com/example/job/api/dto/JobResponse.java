package com.example.job.api.dto;

import com.example.job.common.entity.Job;
import com.example.job.common.enums.JobStatus;
import java.time.Instant;
import java.util.UUID;

public class JobResponse {

    private UUID id;
    private String name;
    private JobStatus status;
    private String payload;
    private String result;
    private String error;
    private int attemptCount;
    private int maxAttempts;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    public static JobResponse from(Job job) {
        JobResponse response = new JobResponse();
        response.id = job.getId();
        response.name = job.getName();
        response.status = job.getStatus();
        response.payload = job.getPayload();
        response.result = job.getResult();
        response.error = job.getError();
        response.attemptCount = job.getAttemptCount();
        response.maxAttempts = job.getMaxAttempts();
        response.createdAt = job.getCreatedAt();
        response.startedAt = job.getStartedAt();
        response.completedAt = job.getCompletedAt();
        response.updatedAt = job.getUpdatedAt();
        return response;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public String getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
