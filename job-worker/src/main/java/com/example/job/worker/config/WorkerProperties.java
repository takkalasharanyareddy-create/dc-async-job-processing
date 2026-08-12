package com.example.job.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {

    private int poolSize = 5;
    private int maxAttempts = 3;
    private long pollIntervalMs = 500;
    private long processingDelayMs = 3000;
    /** Max time a job may stay RUNNING before DEAD + DLQ (default 1 hour). */
    private long runningTimeoutMs = 3_600_000L;
    /** How often to scan for stuck RUNNING jobs. */
    private long timeoutCheckIntervalMs = 60_000L;

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getProcessingDelayMs() {
        return processingDelayMs;
    }

    public void setProcessingDelayMs(long processingDelayMs) {
        this.processingDelayMs = processingDelayMs;
    }

    public long getRunningTimeoutMs() {
        return runningTimeoutMs;
    }

    public void setRunningTimeoutMs(long runningTimeoutMs) {
        this.runningTimeoutMs = runningTimeoutMs;
    }

    public long getTimeoutCheckIntervalMs() {
        return timeoutCheckIntervalMs;
    }

    public void setTimeoutCheckIntervalMs(long timeoutCheckIntervalMs) {
        this.timeoutCheckIntervalMs = timeoutCheckIntervalMs;
    }
}
