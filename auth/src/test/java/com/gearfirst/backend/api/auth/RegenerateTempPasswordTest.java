package com.gearfirst.backend.api.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gearfirst.backend.api.auth.entity.Auth;
import com.gearfirst.backend.api.auth.repository.AuthRepository;
import com.gearfirst.backend.api.mail.MailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** C1: 임시 비밀번호 재발급 요청에 남의 회사 이메일과 공격자 이메일을 넣는 공격. */
@SpringBootTest
@AutoConfigureMockMvc
class RegenerateTempPasswordTest {

    private static final String URL = "/api/v1/auth/regenerate-temp-password";
    private static final String VICTIM = "victim@gearfirst.com";
    private static final String VICTIM_PERSONAL = "victim@gmail.com";
    private static final String ATTACKER = "attacker@evil.com";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthRepository authRepository;

    @MockitoBean
    MailService mailService;

    @AfterEach
    void tearDown() {
        authRepository.deleteAll();
    }

    @Test
    void 남의_계정_임시비밀번호가_공격자_메일로_가지_않는다() throws Exception {
        saveAccount(VICTIM, VICTIM_PERSONAL);

        regenerate("""
                {"email": "%s", "personalEmail": "%s"}
                """
                .formatted(VICTIM, ATTACKER));

        verify(mailService, never()).sendUserRegistrationMail(eq(ATTACKER), anyString());
        verify(mailService).sendUserRegistrationMail(eq(VICTIM_PERSONAL), anyString());
    }

    @Test
    void 가입_때_등록한_개인_이메일로_재발급된다() throws Exception {
        // signup은 user 서비스의 서비스 토큰이 있어야 불린다(GEA-13). 여기선 계정을 만드는 준비 단계
        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(InternalApiCallerTest.serviceToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"userId": 1, "email": "%s", "personalEmail": "%s"}
                                """
                                        .formatted(VICTIM, VICTIM_PERSONAL)))
                .andExpect(status().isCreated());

        regenerate("""
                        {"email": "%s"}
                        """.formatted(VICTIM))
                .andExpect(status().isCreated());

        // 가입 1번 + 재발급 1번
        verify(mailService, times(2)).sendUserRegistrationMail(eq(VICTIM_PERSONAL), anyString());
    }

    @Test
    void 없는_계정이면_404이고_메일을_보내지_않는다() throws Exception {
        regenerate(
                        """
                        {"email": "nobody@gearfirst.com", "personalEmail": "%s"}
                        """
                                .formatted(ATTACKER))
                .andExpect(status().isNotFound());

        verify(mailService, never()).sendUserRegistrationMail(anyString(), anyString());
    }

    @Test
    void 개인_이메일이_등록되지_않은_계정이면_거부하고_메일을_보내지_않는다() throws Exception {
        saveAccount(VICTIM, null);

        regenerate("""
                        {"email": "%s", "personalEmail": "%s"}
                        """
                        .formatted(VICTIM, ATTACKER))
                .andExpect(status().isBadRequest());

        verify(mailService, never()).sendUserRegistrationMail(anyString(), anyString());
    }

    @Test
    void 이메일이_비었으면_400이고_메일을_보내지_않는다() throws Exception {
        regenerate("""
                        {"email": ""}
                        """)
                .andExpect(status().isBadRequest());

        verify(mailService, never()).sendUserRegistrationMail(anyString(), anyString());
    }

    @Test
    void 이메일_형식이_틀리면_400이고_메일을_보내지_않는다() throws Exception {
        regenerate("""
                        {"email": "abc"}
                        """)
                .andExpect(status().isBadRequest());

        verify(mailService, never()).sendUserRegistrationMail(anyString(), anyString());
    }

    private void saveAccount(String email, String personalEmail) {
        authRepository.save(Auth.builder()
                .userId(1L)
                .email(email)
                .password("encoded")
                .personalEmail(personalEmail)
                .build());
    }

    private ResultActions regenerate(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
