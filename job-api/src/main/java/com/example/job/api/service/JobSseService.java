package com.example.job.api.service;

import com.example.job.api.dto.JobResponse;
import com.example.job.common.entity.Job;
import com.example.job.common.enums.JobStatus;
import com.example.job.common.repository.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class JobSseService {

    private static final Logger log = LoggerFactory.getLogger(JobSseService.class);

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final List<ClientSubscription> clients = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> lastPayloadByClient = new ConcurrentHashMap<>();

    public JobSseService(JobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(JobStatus status) {
        SseEmitter emitter = new SseEmitter(0L);
        UUID clientId = UUID.randomUUID();
        ClientSubscription subscription = new ClientSubscription(clientId, emitter, status);
        clients.add(subscription);

        emitter.onCompletion(() -> removeClient(clientId));
        emitter.onTimeout(() -> removeClient(clientId));
        emitter.onError(ex -> removeClient(clientId));

        try {
            sendEvent(emitter, "connected", Map.of("clientId", clientId.toString()));
            pushToClient(subscription, true);
        } catch (IOException e) {
            removeClient(clientId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void publishSnapshot() {
        for (ClientSubscription client : clients) {
            try {
                pushToClient(client, true);
            } catch (IOException e) {
                removeClient(client.id());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.sse.poll-interval-ms:1000}")
    public void pollAndPush() {
        for (ClientSubscription client : clients) {
            try {
                pushToClient(client, false);
            } catch (IOException e) {
                removeClient(client.id());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        for (ClientSubscription client : clients) {
            try {
                sendEvent(client.emitter(), "heartbeat", Map.of("ts", System.currentTimeMillis()));
            } catch (IOException e) {
                removeClient(client.id());
            }
        }
    }

    private void pushToClient(ClientSubscription client, boolean force) throws IOException {
        List<JobResponse> jobs = listJobs(client.status());
        String payload = objectMapper.writeValueAsString(jobs);
        String previous = lastPayloadByClient.get(client.id());
        if (!force && payload.equals(previous)) {
            return;
        }
        lastPayloadByClient.put(client.id(), payload);
        client.emitter().send(SseEmitter.event()
                .name("snapshot")
                .data(payload, MediaType.APPLICATION_JSON));
    }

    private List<JobResponse> listJobs(JobStatus status) {
        List<Job> jobs = status == null
                ? jobRepository.findAllByOrderByCreatedAtDesc()
                : jobRepository.findByStatusOrderByCreatedAtDesc(status);
        return jobs.stream().map(JobResponse::from).toList();
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(name)
                .data(data, MediaType.APPLICATION_JSON));
    }

    private void removeClient(UUID clientId) {
        clients.removeIf(client -> client.id().equals(clientId));
        lastPayloadByClient.remove(clientId);
        log.debug("SSE client disconnected: {}", clientId);
    }

    private record ClientSubscription(UUID id, SseEmitter emitter, JobStatus status) {
    }
}
