package com.gearfirst.user_be.workType.service;

import com.gearfirst.user_be.workType.dto.WorkTypeResponse;
import com.gearfirst.user_be.workType.entity.WorkTypeEntity;
import com.gearfirst.user_be.workType.repository.WorkTypeRepositoy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkTypeService {
    private final WorkTypeRepositoy workTypeRepositoy;

    public List<WorkTypeResponse> getWorkType() {
        List<WorkTypeEntity> entities = workTypeRepositoy.findAll();

        return entities.stream()
                .map(r -> WorkTypeResponse.builder()
                        .workTypeId(r.getId())
                        .workTypeName(r.getWorkTypeName())
                        .build())
                .toList();
    }
}
