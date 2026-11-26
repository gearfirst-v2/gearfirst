package com.gearfirst.backend.api.schedule.repository;

import com.gearfirst.backend.api.schedule.entity.ScheduledTask;
import com.gearfirst.backend.api.schedule.enums.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {
    List<ScheduledTask> findByStatusInAndRunAtAfter(List<TaskStatus> statuses, LocalDateTime now);

    List<ScheduledTask> findByStatusInAndRunAtLessThanEqual(List<TaskStatus> statuses, LocalDateTime time);

    boolean existsByOrderIdAndStatusIn(Long orderId, List<TaskStatus> statuses);
}
