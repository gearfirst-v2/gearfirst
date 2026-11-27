package com.gearfirst.user_be.workType.controller;

import com.gearfirst.user_be.common.config.annotation.CurrentUser;
import com.gearfirst.user_be.common.context.UserContext;
import com.gearfirst.user_be.common.response.ApiResponse;
import com.gearfirst.user_be.common.response.SuccessStatus;
import com.gearfirst.user_be.workType.dto.WorkTypeResponse;
import com.gearfirst.user_be.workType.service.WorkTypeService;
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
@Tag(name = "Get WorkType API", description = "지점 API")
public class WorkTypeController {
    private final WorkTypeService workTypeService;

    @Operation(summary = "지점 리스트 조회", description = "근무 지점 리스트를 조회한다.")
    @GetMapping("getWorkType")
    public ResponseEntity<ApiResponse<List<WorkTypeResponse>>> getWorkType(
            @CurrentUser UserContext caller) { // 로그인한 직원만(헤더 없으면 401)
        List<WorkTypeResponse> response = workTypeService.getWorkType();

        return ApiResponse.success(SuccessStatus.GET_WORKTYPE_SUCCESS, response);
    }
}
