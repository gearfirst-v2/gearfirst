package com.gearfirst.backend.api.auth;

import static com.gearfirst.backend.api.auth.InternalApiCallerTest.serviceToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gearfirst.backend.api.auth.entity.Auth;
import com.gearfirst.backend.api.auth.repository.AuthRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** C3: change-password가 body의 userId를 믿고, 현재 비밀번호 대입에 횟수 제한이 없는 문제. */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordTest {

    private static final String URL = "/api/v1/auth/change-password";
    private static final String ME_PW = "MyPass1!";
    private static final String OTHER_PW = "OtherPass1!";
    private static final String NEW_PW = "NewPass1!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthRepository authRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        save(1L, "me@gearfirst.com", ME_PW);
        save(2L, "other@gearfirst.com", OTHER_PW);
    }

    @AfterEach
    void tearDown() {
        authRepository.deleteAll();
    }

    @Test
    void 토큰이_없으면_401이고_바뀌지_않는다() throws Exception {
        change(null, body(null, ME_PW)).andExpect(status().isUnauthorized());

        assertPassword(1L, ME_PW);
    }

    @Test
    void 서비스_토큰으로는_바꿀_수_없다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(serviceToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, ME_PW)))
                .andExpect(status().isForbidden());

        assertPassword(1L, ME_PW);
    }

    @Test
    void 내_토큰이면_내_비밀번호가_바뀐다() throws Exception {
        change(1L, body(null, ME_PW)).andExpect(status().isOk());

        assertPassword(1L, NEW_PW);
    }

    @Test
    void body에_남의_userId를_넣어도_무시하고_내_계정만_바뀐다() throws Exception {
        change(1L, body(2L, ME_PW)).andExpect(status().isOk());

        assertPassword(1L, NEW_PW);
        assertPassword(2L, OTHER_PW);
    }

    @Test
    void 남의_비밀번호를_현재_비밀번호로_넣어도_남의_계정은_안_바뀐다() throws Exception {
        change(1L, body(2L, OTHER_PW)).andExpect(status().isBadRequest());

        assertPassword(1L, ME_PW);
        assertPassword(2L, OTHER_PW);
    }

    @Test
    void 현재_비밀번호가_틀리면_401이_아니라_400() throws Exception {
        change(1L, body(null, "Wrong1!x")).andExpect(status().isBadRequest());
    }

    @Test
    void 연속_5번_틀리면_맞는_비밀번호도_423으로_거부한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            change(1L, body(null, "Wrong1!x")).andExpect(status().isBadRequest());
        }

        change(1L, body(null, ME_PW)).andExpect(status().isLocked());

        assertPassword(1L, ME_PW);
    }

    @Test
    void 잠긴_계정은_틀린_비밀번호에도_423() throws Exception {
        for (int i = 0; i < 5; i++) {
            change(1L, body(null, "Wrong1!x"));
        }

        change(1L, body(null, "Wrong1!x")).andExpect(status().isLocked());
    }

    @Test
    void 성공하면_실패_횟수가_초기화된다() throws Exception {
        for (int i = 0; i < 4; i++) {
            change(1L, body(null, "Wrong1!x")).andExpect(status().isBadRequest());
        }
        change(1L, body(null, ME_PW)).andExpect(status().isOk());

        // 초기화되지 않았다면 여기서 5번째 실패가 되어 다음 요청이 잠긴다
        change(1L, body(null, "Wrong1!x")).andExpect(status().isBadRequest());
        change(
                        1L,
                        """
                {"currentPassword": "%s", "newPassword": "Another1!", "confirmPassword": "Another1!"}
                """
                                .formatted(NEW_PW))
                .andExpect(status().isOk());
    }

    @Test
    void 남의_계정_실패는_내_계정을_잠그지_않는다() throws Exception {
        for (int i = 0; i < 5; i++) {
            change(2L, body(null, "Wrong1!x"));
        }

        change(1L, body(null, ME_PW)).andExpect(status().isOk());
    }

    @Test
    void 잠금_시간이_지나면_다시_바꿀_수_있다() throws Exception {
        for (int i = 0; i < 5; i++) {
            change(1L, body(null, "Wrong1!x"));
        }
        jdbcTemplate.update(
                "update auth set locked_until = ? where user_id = 1",
                LocalDateTime.now().minusSeconds(1));

        change(1L, body(null, ME_PW)).andExpect(status().isOk());
    }

    @Test
    void 잠금_칸이_비어_있는_기존_계정도_바꿀_수_있다() throws Exception {
        jdbcTemplate.update("update auth set failed_count = null, locked_until = null where user_id = 1");

        change(1L, body(null, ME_PW)).andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호가_비었으면_400이고_실패로_세지_않는다() throws Exception {
        for (int i = 0; i < 5; i++) {
            change(1L, body(null, "")).andExpect(status().isBadRequest());
        }

        change(1L, body(null, ME_PW)).andExpect(status().isOk());
    }

    @Test
    void 새_비밀번호_확인이_다르면_400이고_바뀌지_않는다() throws Exception {
        change(
                        1L,
                        """
                {"currentPassword": "%s", "newPassword": "%s", "confirmPassword": "Different1!"}
                """
                                .formatted(ME_PW, NEW_PW))
                .andExpect(status().isBadRequest());

        assertPassword(1L, ME_PW);
    }

    private ResultActions change(Long tokenUserId, String body) throws Exception {
        var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(body);
        if (tokenUserId != null) {
            request.with(userToken(tokenUserId));
        }
        return mockMvc.perform(request);
    }

    private static JwtRequestPostProcessor userToken(Long userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("SCOPE_openid"));
    }

    private static String body(Long userId, String currentPassword) {
        String userIdField = userId == null ? "" : "\"userId\": %d, ".formatted(userId);
        return """
                {%s"currentPassword": "%s", "newPassword": "%s", "confirmPassword": "%s"}
                """
                .formatted(userIdField, currentPassword, NEW_PW, NEW_PW);
    }

    private void save(Long userId, String email, String rawPassword) {
        authRepository.save(Auth.builder()
                .userId(userId)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .build());
    }

    private void assertPassword(Long userId, String rawPassword) {
        String encoded = authRepository.findByUserId(userId).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
    }
}
