package com.example.job.api.controller;

import com.example.job.api.dto.CreateJobRequest;
import com.example.job.api.dto.JobResponse;
import com.example.job.api.service.JobService;
import com.example.job.api.service.JobSseService;
import com.example.job.common.enums.JobStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("${app.api.jobs-base-path}")
public class JobController {

    private final JobService jobService;
    private final JobSseService jobSseService;

    public JobController(JobService jobService, JobSseService jobSseService) {
        this.jobService = jobService;
        this.jobSseService = jobSseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.createJob(request);
        jobSseService.publishSnapshot();
        return created;
    }

    /** Fixed SSE path: {jobs-base-path}/stream → /api/jobs/stream */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobs(@RequestParam(required = false) JobStatus status) {
        return jobSseService.subscribe(status);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @GetMapping
    public List<JobResponse> listJobs(@RequestParam(required = false) JobStatus status) {
        return jobService.listJobs(status);
    }
}
