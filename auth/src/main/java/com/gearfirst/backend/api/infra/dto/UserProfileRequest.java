package com.gearfirst.backend.api.infra.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserProfileRequest {
    private String email;
    private String name;
    private String phoneNum;
    private String rank;
    private Long regionId;
    private Long workTypeId;
}
