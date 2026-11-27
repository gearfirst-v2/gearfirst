package com.gearfirst.user_be.workType.repository;

import com.gearfirst.user_be.workType.entity.WorkTypeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTypeRepositoy extends JpaRepository<WorkTypeEntity, Long> {
    Optional<WorkTypeEntity> findByWorkTypeName(String workTypeName);
}
