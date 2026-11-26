package com.gearfirst.user_be.region.service;

import com.gearfirst.user_be.region.dto.RegionResponse;
import com.gearfirst.user_be.region.entity.RegionEntity;
import com.gearfirst.user_be.region.repository.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegionService {
    private final RegionRepository regionRepository;

    public List<RegionResponse> getRegion() {
        List<RegionEntity> entities = regionRepository.findAll();

        return entities.stream()
                .map(r -> RegionResponse.builder()
                        .regionId(r.getId())
                        .regionName(r.getRegionName())
                        .build())
                .toList();
    }
}
