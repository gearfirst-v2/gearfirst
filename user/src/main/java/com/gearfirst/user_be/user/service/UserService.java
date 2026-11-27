package com.gearfirst.user_be.user.service;

import com.gearfirst.user_be.common.context.UserContext;
import com.gearfirst.user_be.common.exception.BadRequestException;
import com.gearfirst.user_be.common.exception.ConflictException;
import com.gearfirst.user_be.common.exception.ForbiddenException;
import com.gearfirst.user_be.common.exception.InternalServerException;
import com.gearfirst.user_be.common.exception.NotFoundException;
import com.gearfirst.user_be.common.response.ErrorStatus;
import com.gearfirst.user_be.region.entity.RegionEntity;
import com.gearfirst.user_be.region.repository.RegionRepository;
import com.gearfirst.user_be.user.client.AuthClient;
import com.gearfirst.user_be.user.dto.*;
import com.gearfirst.user_be.user.entity.UserEntity;
import com.gearfirst.user_be.user.enums.Rank;
import com.gearfirst.user_be.user.repository.UserRepository;
import com.gearfirst.user_be.user.spec.UserSpecification;
import com.gearfirst.user_be.workType.entity.WorkTypeEntity;
import com.gearfirst.user_be.workType.repository.WorkTypeRepositoy;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final WorkTypeRepositoy workTypeRepository;
    private final RegionRepository regionRepository;
    private final AuthClient authClient;

    public void updateUser(UserContext caller, UserRequest request) {
        UserAuthorization.requireHeadOfficeLeader(caller);
        UserEntity entity = findUser(request.getUserId());

        // 회사 이메일은 auth 로그인 이메일과 같아야 해서 바꾸지 않는다
        if (request.getEmail() != null && !request.getEmail().equals(entity.getEmail())) {
            throw new BadRequestException(ErrorStatus.EMAIL_NOT_CHANGEABLE.getMessage());
        }
        // 마지막 본사 팀장이 자신을 강등해 관리자가 0명이 되지 않게
        if (UserAuthorization.isSelf(caller, entity.getId()) && changesRole(entity, request)) {
            throw new ForbiddenException(ErrorStatus.FORBIDDEN_SELF_CHANGE.getMessage());
        }

        entity.setName(request.getName());
        entity.setPhoneNum(request.getPhoneNum());
        entity.setRank(request.getRank());

        if (request.getRegionId() != null) {
            RegionEntity region = regionRepository
                    .findById(request.getRegionId())
                    .orElseThrow(() -> new EntityNotFoundException("Region not found"));
            entity.setRegion(region);
        }

        if (request.getWorkTypeId() != null) {
            WorkTypeEntity workType = workTypeRepository
                    .findById(request.getWorkTypeId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkType not found"));
            entity.setWorkType(workType);
        }

        userRepository.save(entity);
    }

    private static boolean changesRole(UserEntity entity, UserRequest request) {
        return request.getRank() != entity.getRank()
                || (request.getRegionId() != null
                        && !Objects.equals(
                                request.getRegionId(), entity.getRegion().getId()))
                || (request.getWorkTypeId() != null
                        && !Objects.equals(
                                request.getWorkTypeId(), entity.getWorkType().getId()));
    }

    public Page<UserResponse> getAllUser(
            UserContext caller, Rank rank, Long workTypeId, Long regionId, String keyword, Pageable pageable) {
        UserAuthorization.requireHeadOffice(caller);
        Specification<UserEntity> spec = UserSpecification.build(rank, workTypeId, regionId, keyword);

        Page<UserEntity> userEntityPage = userRepository.findAll(spec, pageable);

        return userEntityPage.map(this::convertToDto);
    }

    private UserResponse convertToDto(UserEntity entity) {
        return UserResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .phoneNum(entity.getPhoneNum())
                .rank(entity.getRank().getStatus())
                .regionId(entity.getRegion().getId())
                .region(entity.getRegion().getRegionName())
                .workType(entity.getWorkType().getWorkTypeName())
                .workTypeId(entity.getWorkType().getId())
                .email(entity.getEmail())
                .build();
    }

    @Transactional
    public void registerUser(UserContext caller, CreateUserRequest userRequest) {
        UserAuthorization.requireHeadOfficeLeader(caller);
        createUser(userRequest);
    }

    /** 권한 검사 없이 계정을 만든다. registerUser와 첫 본사 계정 부트스트랩이 쓴다 */
    @Transactional
    public void createUser(CreateUserRequest userRequest) {
        RegionEntity region = regionRepository
                .findById(userRequest.getRegionId())
                .orElseThrow(() -> new IllegalArgumentException("지역 정보를 찾을 수 없습니다."));

        WorkTypeEntity workType = workTypeRepository
                .findById(userRequest.getWorkTypeId())
                .orElseThrow(() -> new IllegalArgumentException("근무 지점을 찾을 수 없습니다."));

        if (userRepository.findByEmail(userRequest.getEmail()).isPresent()) {
            throw new ConflictException(ErrorStatus.DUPLICATE_EMAIL_EXCEPTION.getMessage());
        }
        // user 먼저 저장
        UserEntity user = UserEntity.builder()
                .email(userRequest.getEmail())
                .name(userRequest.getName())
                .phoneNum(userRequest.getPhoneNum())
                .workType(workType)
                .region(region)
                .rank(userRequest.getRank())
                .build();

        userRepository.save(user);

        // Auth 서버 호출 서버 실패시 예외 발생 -> 자동 롤백
        try {
            authClient.createAccount(
                    new CreateAccountRequest(user.getId(), user.getEmail(), userRequest.getPersonalEmail()));
        } catch (Exception e) {
            // Auth 서버 통신 실패 시 롤백
            throw new IllegalStateException("Auth 서버 계정 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /** caller가 null이면 헤더 없는 내부 호출(auth의 토큰 발급)이다. GEA-8 경계대로 내부망은 믿는다 */
    public UserResponse getUser(UserContext caller, Long userId) {
        if (caller != null && !UserAuthorization.isSelf(caller, userId)) {
            UserAuthorization.requireHeadOffice(caller);
        }
        UserEntity entity = findUser(userId);

        return UserResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .regionId(entity.getRegion().getId())
                .workTypeId(entity.getWorkType().getId())
                .email(entity.getEmail())
                .phoneNum(entity.getPhoneNum())
                .rank(entity.getRank().getStatus())
                .region(entity.getRegion().getRegionName())
                .workType(entity.getWorkType().getWorkTypeName())
                .build();
    }

    @Transactional
    public void deleteUser(UserContext caller, String email) {
        UserAuthorization.requireHeadOfficeLeader(caller);
        UserEntity entity = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER_EXCEPTION.getMessage()));

        // 마지막 본사 팀장이 자신을 지워 관리자가 0명이 되지 않게
        if (UserAuthorization.isSelf(caller, entity.getId())) {
            throw new ForbiddenException(ErrorStatus.FORBIDDEN_SELF_DELETE.getMessage());
        }
        userRepository.delete(entity);
        try {
            authClient.deleteUser(entity.getId());
        } catch (Exception e) {
            throw new InternalServerException(ErrorStatus.FAIL_DELETE_USER.getMessage());
        }
    }

    private UserEntity findUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER_EXCEPTION.getMessage()));
    }
}
