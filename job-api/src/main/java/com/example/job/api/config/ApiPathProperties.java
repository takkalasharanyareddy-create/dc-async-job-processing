package com.example.job.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.api")
public class ApiPathProperties {

    /**
     * Base path for job APIs, e.g. /api/jobs
     */
    private String jobsBasePath = "/api/jobs";

    /**
     * UI config endpoint used by the frontend.
     */
    private String uiConfigPath = "/api/ui-config";

    /** Fixed relative SSE path under jobs base path. */
    public static final String JOBS_STREAM_PATH = "/stream";

    /** Fixed relative job-by-id path under jobs base path. */
    public static final String JOB_BY_ID_PATH = "/{id}";

    public String getJobsBasePath() {
        return jobsBasePath;
    }

    public void setJobsBasePath(String jobsBasePath) {
        this.jobsBasePath = jobsBasePath;
    }

    public String getUiConfigPath() {
        return uiConfigPath;
    }

    public void setUiConfigPath(String uiConfigPath) {
        this.uiConfigPath = uiConfigPath;
    }

    public String getJobsStreamFullPath() {
        return jobsBasePath + JOBS_STREAM_PATH;
    }
}
