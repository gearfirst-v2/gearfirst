package com.gearfirst.user_be.user.service;

import com.gearfirst.user_be.region.entity.RegionEntity;
import com.gearfirst.user_be.region.repository.RegionRepository;
import com.gearfirst.user_be.user.dto.CreateUserRequest;
import com.gearfirst.user_be.user.enums.Rank;
import com.gearfirst.user_be.user.repository.UserRepository;
import com.gearfirst.user_be.workType.entity.WorkTypeEntity;
import com.gearfirst.user_be.workType.repository.WorkTypeRepositoy;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 첫 본사 계정 (GEA-15). 본사 팀장이 0명일 때만 환경변수의 이메일로 본사 팀장 계정을 만든다.
 * 등록과 같은 흐름이라 임시 비밀번호는 개인 메일로만 간다. 비밀번호는 레포·설정 어디에도 두지 않는다.
 * 본사 팀장을 모두 잃었을 때도 재시작하면 다시 만들어진다(서버 설정을 바꿀 수 있는 사람만 가능).
 */
@Slf4j
@Component
public class HeadOfficeBootstrap {

    static final int MAX_ATTEMPTS = 3;

    private final UserService userService;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final WorkTypeRepositoy workTypeRepository;
    private final String email;
    private final String personalEmail;
    private final String name;
    private final String regionName;
    private final Duration retryDelay;

    public HeadOfficeBootstrap(
            UserService userService,
            UserRepository userRepository,
            RegionRepository regionRepository,
            WorkTypeRepositoy workTypeRepository,
            @Value("${bootstrap.admin.email:}") String email,
            @Value("${bootstrap.admin.personal-email:}") String personalEmail,
            @Value("${bootstrap.admin.name:관리자}") String name,
            @Value("${bootstrap.admin.region:서울}") String regionName,
            @Value("${bootstrap.admin.retry-delay:5s}") Duration retryDelay) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.regionRepository = regionRepository;
        this.workTypeRepository = workTypeRepository;
        this.email = email;
        this.personalEmail = personalEmail;
        this.name = name;
        this.regionName = regionName;
        this.retryDelay = retryDelay;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (email.isBlank() || personalEmail.isBlank()) {
            return;
        }
        if (userRepository.existsByRankAndWorkType_WorkTypeName(Rank.LEADER, UserAuthorization.HEAD_OFFICE)) {
            return;
        }
        RegionEntity region = regionRepository.findByRegionName(regionName).orElse(null);
        WorkTypeEntity workType = workTypeRepository
                .findByWorkTypeName(UserAuthorization.HEAD_OFFICE)
                .orElse(null);
        if (region == null || workType == null) {
            log.error("첫 본사 계정을 만들지 못했어요: 지역 '{}' 또는 근무유형 '본사'가 DB에 없어요", regionName);
            return;
        }

        var request =
                new CreateUserRequest(name, personalEmail, email, null, Rank.LEADER, region.getId(), workType.getId());
        // user가 auth보다 먼저 뜰 수 있어서 몇 번 다시 시도한다. 실패하면 user 쪽 저장도 롤백돼 다음 재시작에 다시 한다
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                userService.createUser(request);
                log.info("첫 본사 계정을 만들었어요: {} (임시 비밀번호는 개인 메일로 발송)", email);
                return;
            } catch (RuntimeException e) {
                log.warn("첫 본사 계정 생성 실패 ({}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                sleep();
            }
        }
        log.error("첫 본사 계정을 만들지 못했어요. auth가 떠 있는지 확인하고 user를 다시 시작하세요");
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
