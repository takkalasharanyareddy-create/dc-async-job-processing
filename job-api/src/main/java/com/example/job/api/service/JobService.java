package com.example.job.api.service;

import com.example.job.api.dto.CreateJobRequest;
import com.example.job.api.dto.JobResponse;
import com.example.job.common.entity.Job;
import com.example.job.common.enums.JobStatus;
import com.example.job.common.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public JobService(JobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        String name = request.getName().trim();
        String details = request.getDetails().trim();

        if (jobRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job name already exists: " + name);
        }

        Job job = new Job();
        job.setName(name);
        job.setPayload(toPayloadJson(name, details));
        job.setStatus(JobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);

        try {
            return JobResponse.from(jobRepository.save(job));
        } catch (DataIntegrityViolationException ex) {
            // race-safe fallback if two requests use the same name at once
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job name already exists: " + name);
        }
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));
        return JobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs(JobStatus status) {
        List<Job> jobs = status == null
                ? jobRepository.findAllByOrderByCreatedAtDesc()
                : jobRepository.findByStatusOrderByCreatedAtDesc(status);
        return jobs.stream().map(JobResponse::from).toList();
    }

    private String toPayloadJson(String name, String details) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("details", details);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode job payload");
        }
    }
}
