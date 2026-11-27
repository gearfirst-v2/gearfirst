package com.gearfirst.user_be.region.controller;

import com.gearfirst.user_be.common.config.annotation.CurrentUser;
import com.gearfirst.user_be.common.context.UserContext;
import com.gearfirst.user_be.common.response.ApiResponse;
import com.gearfirst.user_be.common.response.SuccessStatus;
import com.gearfirst.user_be.region.dto.RegionResponse;
import com.gearfirst.user_be.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Get Region API", description = "지역 API")
public class RegionController {
    private final RegionService regionService;

    @Operation(summary = "근무 지역 리스트 조회", description = "근무지 지역 리스트를 조회한다.")
    @GetMapping("getRegion")
    public ResponseEntity<ApiResponse<List<RegionResponse>>> getRegion(
            @CurrentUser UserContext caller) { // 로그인한 직원만(헤더 없으면 401)
        List<RegionResponse> response = regionService.getRegion();

        return ApiResponse.success(SuccessStatus.GET_REGION_SUCCESS, response);
    }
}
