package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.TaskCompletionLog;

public interface TaskCompletionLogRepository extends JpaRepository<TaskCompletionLog, UUID> {

    long countByUserIdAndTaskName(UUID userId, String taskName);

    Optional<TaskCompletionLog> findFirstByUserIdAndTaskNameAndStatusOrderByStartedAtDesc(
            UUID userId, String taskName, String status);

    List<TaskCompletionLog> findByStatusOrderByCompletedAtDesc(String status);
}
