package com.example.job.common.repository;

import com.example.job.common.entity.Job;
import com.example.job.common.enums.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findAllByOrderByCreatedAtDesc();

    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);

    boolean existsByNameIgnoreCase(String name);

    List<Job> findByStatusAndStartedAtBefore(JobStatus status, Instant startedAtBefore);

    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Job> findNextPendingForUpdate();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Job j
            SET j.status = :running,
                j.startedAt = :startedAt,
                j.updatedAt = :updatedAt,
                j.attemptCount = j.attemptCount + 1
            WHERE j.id = :id AND j.status = :pending
            """)
    int markRunning(@Param("id") UUID id,
                    @Param("running") JobStatus running,
                    @Param("pending") JobStatus pending,
                    @Param("startedAt") Instant startedAt,
                    @Param("updatedAt") Instant updatedAt);
}
