package com.gearfirst.user_be.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gearfirst.user_be.region.entity.RegionEntity;
import com.gearfirst.user_be.region.repository.RegionRepository;
import com.gearfirst.user_be.user.client.AuthClient;
import com.gearfirst.user_be.user.entity.UserEntity;
import com.gearfirst.user_be.user.enums.Rank;
import com.gearfirst.user_be.user.repository.UserRepository;
import com.gearfirst.user_be.workType.entity.WorkTypeEntity;
import com.gearfirst.user_be.workType.repository.WorkTypeRepositoy;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** GEA-15: 빈 DB에서 첫 본사 계정은 환경변수 부트스트랩으로만 만든다 */
@SpringBootTest
class HeadOfficeBootstrapTest {

    private static final String ADMIN = "admin@gearfirst.local";

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RegionRepository regionRepository;

    @Autowired
    WorkTypeRepositoy workTypeRepository;

    @MockitoBean
    AuthClient authClient;

    private RegionEntity seoul;
    private WorkTypeEntity headOffice;

    @BeforeEach
    void setUp() {
        seoul = regionRepository.save(RegionEntity.builder().regionName("서울").build());
        headOffice = workTypeRepository.save(
                WorkTypeEntity.builder().workTypeName("본사").build());
    }

    // 트랜잭션 롤백 대신 직접 지운다: 실패 시 롤백되는지가 검증 대상이라 테스트 트랜잭션에 묶으면 안 된다
    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        workTypeRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 본사_팀장이_없으면_환경변수_이메일로_만들고_auth_계정도_만든다() {
        bootstrap(ADMIN).run();

        assertThat(userRepository.findByEmail(ADMIN)).isPresent();
        assertThat(userRepository.existsByRankAndWorkType_WorkTypeName(Rank.LEADER, "본사"))
                .isTrue();
        verify(authClient).createAccount(any());
    }

    @Test
    void 본사_팀장이_이미_있으면_아무것도_안_한다() {
        userRepository.save(UserEntity.builder()
                .email("head@gearfirst.local")
                .name("head")
                .rank(Rank.LEADER)
                .region(seoul)
                .workType(headOffice)
                .build());

        bootstrap(ADMIN).run();

        assertThat(userRepository.findByEmail(ADMIN)).isEmpty();
        verify(authClient, never()).createAccount(any());
    }

    @Test
    void 이메일_설정이_없으면_아무것도_안_한다() {
        bootstrap("").run();

        assertThat(userRepository.count()).isZero();
        verify(authClient, never()).createAccount(any());
    }

    @Test
    void auth가_계속_실패하면_재시도_후_포기하고_user_저장도_남기지_않는다() {
        doThrow(new RuntimeException("auth down")).when(authClient).createAccount(any());

        bootstrap(ADMIN).run(); // 예외로 user 시작을 막지 않는다

        verify(authClient, times(HeadOfficeBootstrap.MAX_ATTEMPTS)).createAccount(any());
        assertThat(userRepository.findByEmail(ADMIN)).isEmpty(); // 다음 재시작에 다시 시도할 수 있게
    }

    private HeadOfficeBootstrap bootstrap(String email) {
        return new HeadOfficeBootstrap(
                userService,
                userRepository,
                regionRepository,
                workTypeRepository,
                email,
                "admin-personal@gearfirst.local",
                "관리자",
                "서울",
                Duration.ZERO);
    }
}
