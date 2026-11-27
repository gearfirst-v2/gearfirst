package com.gearfirst.user_be.user.controller;

import com.gearfirst.user_be.common.config.annotation.CurrentUser;
import com.gearfirst.user_be.common.context.UserContext;
import com.gearfirst.user_be.common.context.UserContextHolder;
import com.gearfirst.user_be.common.response.ApiResponse;
import com.gearfirst.user_be.common.response.SuccessStatus;
import com.gearfirst.user_be.user.dto.*;
import com.gearfirst.user_be.user.enums.Rank;
import com.gearfirst.user_be.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Get User API", description = "유저 API")
public class UserController {
    private final UserService userService;

    @Operation(summary = "회원 등록", description = "신규 회원을 등록한다.")
    @PostMapping("/registerUser")
    public ResponseEntity<ApiResponse<Void>> registerUser(
            @CurrentUser UserContext caller, @RequestBody CreateUserRequest userRequest) {
        userService.registerUser(caller, userRequest);
        return ApiResponse.success_only(SuccessStatus.REGIST_USER_SUCCESS);
    }

    @Operation(summary = "전체 사용자 조회", description = "전체 사용자 리스트를 조회한다.")
    @GetMapping("getAllUser")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUser(
            @CurrentUser UserContext caller,
            @RequestParam(required = false) Rank rank,
            @RequestParam(required = false) Long workTypeId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        Page<UserResponse> userList = userService.getAllUser(caller, rank, workTypeId, regionId, keyword, pageable);
        PageResponse<UserResponse> response = new PageResponse<>(
                userList.getContent(),
                userList.getNumber(),
                userList.getSize(),
                userList.getTotalElements(),
                userList.getTotalPages());

        return ApiResponse.success(SuccessStatus.GET_ALL_USER_SUCCESS, response);
    }

    @Operation(summary = "회원 정보 수정", description = "기존 회원 정보를 수정한다.")
    @PostMapping("updateUser")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @CurrentUser UserContext caller, @RequestBody UserRequest request) {
        userService.updateUser(caller, request);

        return ApiResponse.success_only(SuccessStatus.UPDATE_USER_SUCCESS);
    }

    @Operation(summary = "사용자 조회", description = "특정 사용자를 조회한다.")
    @GetMapping("getUser")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@RequestParam Long userId) {
        // @CurrentUser를 쓰지 않는다: 헤더 없는 auth 내부 호출도 받아야 해서 null을 허용한다
        UserResponse entity = userService.getUser(UserContextHolder.get(), userId);

        return ApiResponse.success(SuccessStatus.GET_USER_SUCCESS, entity);
    }

    @Operation(summary = "사용자 삭제", description = "기존 사용자를 삭제한다.")
    @DeleteMapping("/deleteUser")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@CurrentUser UserContext caller, @RequestParam String email) {
        userService.deleteUser(caller, email);

        return ApiResponse.success_only(SuccessStatus.DELETE_USER_SUCCESS);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<String>> me(@CurrentUser UserContext user) {
        String response = user.getWorkType();
        return ApiResponse.success(SuccessStatus.GET_USER_WORK_TYPE_SUCCESS, response);
    }
}
