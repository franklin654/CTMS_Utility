package com.ctms.ctms_backend.task.repository;

import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.entity.TaskStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByOwnerIdOrderByDueAtAsc(Long ownerId, Pageable pageable);

    List<Task> findByStatusInAndEscalatedFalseAndDueAtBefore(
            List<TaskStatus> statuses, Instant cutoff);
}
