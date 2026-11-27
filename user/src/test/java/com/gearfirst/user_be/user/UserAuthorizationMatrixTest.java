package com.gearfirst.user_be.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gearfirst.user_be.region.entity.RegionEntity;
import com.gearfirst.user_be.region.repository.RegionRepository;
import com.gearfirst.user_be.user.client.AuthClient;
import com.gearfirst.user_be.user.entity.UserEntity;
import com.gearfirst.user_be.user.enums.Rank;
import com.gearfirst.user_be.user.repository.UserRepository;
import com.gearfirst.user_be.workType.entity.WorkTypeEntity;
import com.gearfirst.user_be.workType.repository.WorkTypeRepositoy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * GEA-15 (B1, B2): user API 권한표를 칸마다 검증한다. 권한표는 docs/security/B1-B2-user-authz.md와 같다.
 * 호출자 정보는 gateway가 토큰 클레임으로 넣는 X-User-* 헤더(base64)로 보낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAuthorizationMatrixTest {

    enum Caller {
        HQ_LEADER("본사", Rank.LEADER),
        HQ_EMPLOYEE("본사", Rank.EMPLOYEE),
        WAREHOUSE("창고", Rank.WAREHOUSE_MANAGER),
        BRANCH("대리점", Rank.BRANCH_ENGINEER);

        final String workType;
        final Rank rank;

        Caller(String workType, Rank rank) {
            this.workType = workType;
            this.rank = rank;
        }
    }

    enum Api {
        REGISTER_USER,
        GET_ALL_USER,
        GET_USER,
        UPDATE_USER,
        DELETE_USER,
        ME,
        GET_REGION,
        GET_WORK_TYPE
    }

    /** 대상: 없음(목록·등록 등), 다른 직원, 호출자 자신 */
    enum Target {
        NONE,
        OTHER,
        SELF
    }

    private static final int OK = 200;
    private static final int NO = 403;

    // 권한표. 열 순서: HQ_LEADER, HQ_EMPLOYEE, WAREHOUSE, BRANCH
    private static final Object[][] MATRIX = {
        {Api.REGISTER_USER, Target.NONE, OK, NO, NO, NO},
        {Api.GET_ALL_USER, Target.NONE, OK, OK, NO, NO},
        {Api.GET_USER, Target.OTHER, OK, OK, NO, NO},
        {Api.GET_USER, Target.SELF, OK, OK, OK, OK},
        {Api.UPDATE_USER, Target.OTHER, OK, NO, NO, NO},
        {Api.UPDATE_USER, Target.SELF, OK, NO, NO, NO},
        {Api.DELETE_USER, Target.OTHER, OK, NO, NO, NO},
        {Api.DELETE_USER, Target.SELF, NO, NO, NO, NO}, // 마지막 본사 팀장이 자신을 지우지 못하게
        {Api.ME, Target.NONE, OK, OK, OK, OK},
        {Api.GET_REGION, Target.NONE, OK, OK, OK, OK},
        {Api.GET_WORK_TYPE, Target.NONE, OK, OK, OK, OK},
    };

    static Stream<Arguments> matrix() {
        return Arrays.stream(MATRIX).flatMap(row -> Arrays.stream(Caller.values())
                .map(caller -> Arguments.of(row[0], row[1], caller, row[2 + caller.ordinal()])));
    }

    // getUser는 헤더 없이도 허용한다(auth 내부 호출). 아래 테스트에서 따로 본다
    static Stream<Arguments> apiTargets() {
        return Arrays.stream(MATRIX).filter(row -> row[0] != Api.GET_USER).map(row -> Arguments.of(row[0], row[1]));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RegionRepository regionRepository;

    @Autowired
    WorkTypeRepositoy workTypeRepository;

    @MockitoBean
    AuthClient authClient;

    private final Map<String, WorkTypeEntity> workTypes = new HashMap<>();
    private final Map<Caller, UserEntity> callers = new EnumMap<>(Caller.class);
    private RegionEntity seoul;
    private UserEntity other;

    @BeforeEach
    void setUp() {
        seoul = regionRepository.save(RegionEntity.builder().regionName("서울").build());
        for (String name : new String[] {"본사", "창고", "대리점"}) {
            workTypes.put(
                    name,
                    workTypeRepository.save(
                            WorkTypeEntity.builder().workTypeName(name).build()));
        }
        for (Caller caller : Caller.values()) {
            callers.put(
                    caller, saveUser(caller.name().toLowerCase() + "@gearfirst.local", caller.workType, caller.rank));
        }
        other = saveUser("other@gearfirst.local", "대리점", Rank.EMPLOYEE);
    }

    @ParameterizedTest(name = "{0} {1} by {2} -> {3}")
    @MethodSource("matrix")
    void 권한표_칸마다_허용은_200_거부는_403(Api api, Target target, Caller caller, int expected) throws Exception {
        UserEntity me = callers.get(caller);
        UserEntity targetUser = target == Target.SELF ? me : other;

        mvc.perform(withCaller(request(api, targetUser), me)).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "{0} {1} without login -> 401")
    @MethodSource("apiTargets")
    void 호출자_정보가_없으면_401(Api api, Target target) throws Exception {
        mvc.perform(request(api, other)).andExpect(status().isUnauthorized());
    }

    @Test
    void 헤더_없는_getUser는_auth_내부_호출로_보고_허용() throws Exception {
        // auth가 토큰을 만들 때 헤더 없이 부른다. 외부 요청은 gateway가 항상 헤더를 붙이므로 이 경로로 올 수 없다 (GEA-8 경계)
        mvc.perform(request(Api.GET_USER, other)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "rank header {0} -> 403")
    @EnumSource(
            value = Rank.class,
            names = {"EMPLOYEE", "WAREHOUSE_MANAGER", "BRANCH_ENGINEER"})
    void 본사라도_팀장이_아니면_팀장_계정_등록_거부(Rank callerRank) throws Exception {
        // B1 재현: 요청 body가 아니라 호출자 직급으로 판단해야 한다
        UserEntity caller = callers.get(Caller.HQ_EMPLOYEE);
        mvc.perform(withHeaders(request(Api.REGISTER_USER, other), caller.getId(), "본사", callerRank.getStatus()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 알_수_없는_직급_헤더는_거부() throws Exception {
        UserEntity caller = callers.get(Caller.HQ_LEADER);
        mvc.perform(withHeaders(request(Api.REGISTER_USER, other), caller.getId(), "본사", "회장"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 사원이_자기_직급을_팀장으로_올릴_수_없다() throws Exception {
        // B2 재현: 본인 수정으로 권한 상승
        UserEntity me = callers.get(Caller.HQ_EMPLOYEE);
        mvc.perform(withCaller(updateRequest(me, Rank.LEADER), me)).andExpect(status().isForbidden());
    }

    @Test
    void 본사_팀장도_자기_직급을_바꿀_수_없다() throws Exception {
        // 마지막 본사 팀장이 자신을 강등하면 관리자가 0명이 된다
        UserEntity me = callers.get(Caller.HQ_LEADER);
        mvc.perform(withCaller(updateRequest(me, Rank.EMPLOYEE), me)).andExpect(status().isForbidden());
    }

    @Test
    void 본사_팀장도_자기_근무유형을_바꿀_수_없다() throws Exception {
        UserEntity me = callers.get(Caller.HQ_LEADER);
        mvc.perform(withCaller(
                        updateRequest(me, me.getRank(), workTypes.get("대리점").getId(), me.getEmail()), me))
                .andExpect(status().isForbidden());
    }

    @Test
    void 본사_팀장은_다른_직원의_직급과_근무유형을_바꿀_수_있다() throws Exception {
        UserEntity me = callers.get(Caller.HQ_LEADER);
        mvc.perform(withCaller(
                        updateRequest(other, Rank.LEADER, workTypes.get("창고").getId(), other.getEmail()), me))
                .andExpect(status().isOk());
    }

    @Test
    void 회사_이메일은_바꿀_수_없다() throws Exception {
        // auth 로그인 이메일과 어긋나지 않게 막는다
        UserEntity me = callers.get(Caller.HQ_LEADER);
        mvc.perform(withCaller(updateRequest(other, other.getRank(), null, "changed@gearfirst.local"), me))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_직원_수정은_404() throws Exception {
        UserEntity me = callers.get(Caller.HQ_LEADER);
        UserEntity ghost = saveUser("ghost@gearfirst.local", "대리점", Rank.EMPLOYEE);
        userRepository.delete(ghost);
        mvc.perform(withCaller(updateRequest(ghost, Rank.EMPLOYEE), me)).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder request(Api api, UserEntity target) {
        return switch (api) {
            case REGISTER_USER ->
                post("/api/v1/registerUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                            {"name":"새직원","personalEmail":"new-personal@gearfirst.local",
                             "email":"new@gearfirst.local","phoneNum":"01012345678",
                             "rank":"LEADER","regionId":%d,"workTypeId":%d}
                            """
                                        .formatted(
                                                seoul.getId(),
                                                workTypes.get("본사").getId()));
            case GET_ALL_USER -> get("/api/v1/getAllUser");
            case GET_USER ->
                get("/api/v1/getUser").param("userId", target.getId().toString());
            case UPDATE_USER -> updateRequest(target, target.getRank());
            case DELETE_USER -> delete("/api/v1/deleteUser").param("email", target.getEmail());
            case ME -> get("/api/v1/me");
            case GET_REGION -> get("/api/v1/getRegion");
            case GET_WORK_TYPE -> get("/api/v1/getWorkType");
        };
    }

    private MockHttpServletRequestBuilder updateRequest(UserEntity target, Rank rank) {
        return updateRequest(target, rank, null, target.getEmail());
    }

    private MockHttpServletRequestBuilder updateRequest(UserEntity target, Rank rank, Long workTypeId, String email) {
        return post("/api/v1/updateUser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"userId":%d,"name":"%s","email":"%s","phoneNum":"01000000000","rank":"%s","workTypeId":%s}
                        """
                                .formatted(target.getId(), target.getName(), email, rank.name(), workTypeId));
    }

    private MockHttpServletRequestBuilder withCaller(MockHttpServletRequestBuilder req, UserEntity caller) {
        return withHeaders(
                req,
                caller.getId(),
                caller.getWorkType().getWorkTypeName(),
                caller.getRank().getStatus());
    }

    // gateway가 넣는 헤더와 같은 형식: 값은 토큰 클레임(직급·근무유형은 한글 이름)을 base64로
    private static MockHttpServletRequestBuilder withHeaders(
            MockHttpServletRequestBuilder req, Long userId, String workType, String rank) {
        return req.header("X-User-Id", userId.toString())
                .header("X-User-Name", b64("테스트"))
                .header("X-User-Rank", b64(rank))
                .header("X-User-Region", b64("서울"))
                .header("X-User-WorkType", b64(workType));
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private UserEntity saveUser(String email, String workType, Rank rank) {
        return userRepository.save(UserEntity.builder()
                .email(email)
                .name(email.substring(0, email.indexOf('@')))
                .phoneNum("01000000000")
                .rank(rank)
                .region(seoul)
                .workType(workTypes.get(workType))
                .build());
    }
}
