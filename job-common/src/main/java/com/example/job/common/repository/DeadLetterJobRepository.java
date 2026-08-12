package com.example.job.common.repository;

import com.example.job.common.entity.DeadLetterJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterJobRepository extends JpaRepository<DeadLetterJob, UUID> {
}
