package com.gearfirst.backend.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gearfirst.backend.api.auth.entity.Auth;
import com.gearfirst.backend.api.auth.repository.AuthRepository;
import com.gearfirst.backend.api.mail.MailService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** C2·signup: user 서비스만 불러야 하는 signup, delete를 누구나 부르는 공격. */
@SpringBootTest
@AutoConfigureMockMvc
class InternalApiCallerTest {

    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String DELETE = "/api/v1/auth/delete";
    private static final String SIGNUP_BODY =
            """
            {"userId": 1, "email": "new@gearfirst.com", "personalEmail": "attacker@evil.com"}
            """;

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

    // --- delete (C2) ---

    @Test
    void delete_토큰이_없으면_401이고_지우지_않는다() throws Exception {
        saveAccount();

        mockMvc.perform(delete()).andExpect(status().isUnauthorized());

        assertThat(authRepository.findByUserId(1L)).isPresent();
    }

    @Test
    void delete_사용자_토큰이면_403이고_지우지_않는다() throws Exception {
        saveAccount();

        mockMvc.perform(delete().with(userToken())).andExpect(status().isForbidden());

        assertThat(authRepository.findByUserId(1L)).isPresent();
    }

    @Test
    void delete_서비스_토큰이면_지운다() throws Exception {
        saveAccount();

        mockMvc.perform(delete().with(serviceToken())).andExpect(status().isOk());

        assertThat(authRepository.findByUserId(1L)).isEmpty();
    }

    // --- signup ---

    @Test
    void signup_토큰이_없으면_401이고_계정도_메일도_없다() throws Exception {
        mockMvc.perform(signup()).andExpect(status().isUnauthorized());

        assertThat(authRepository.findByUserId(1L)).isEmpty();
        verify(mailService, never()).sendUserRegistrationMail(anyString(), anyString());
    }

    @Test
    void signup_사용자_토큰이면_403이고_계정도_메일도_없다() throws Exception {
        mockMvc.perform(signup().with(userToken())).andExpect(status().isForbidden());

        assertThat(authRepository.findByUserId(1L)).isEmpty();
        verify(mailService, never()).sendUserRegistrationMail(anyString(), anyString());
    }

    @Test
    void signup_서비스_토큰이면_계정을_만든다() throws Exception {
        mockMvc.perform(signup().with(serviceToken())).andExpect(status().isCreated());

        assertThat(authRepository.findByUserId(1L)).isPresent();
    }

    @Test
    void signup_가짜_서명_토큰이면_401() throws Exception {
        mockMvc.perform(signup().header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());

        assertThat(authRepository.findByUserId(1L)).isEmpty();
    }

    // --- 실제 client credentials 흐름: auth가 발급한 서비스 토큰으로 signup ---

    @Test
    void user_service가_client_credentials로_받은_토큰으로_signup할_수_있다() throws Exception {
        String body = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("user-service", "test-user-service-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "internal"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(body, "$.access_token");

        mockMvc.perform(signup().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        assertThat(authRepository.findByUserId(1L)).isPresent();
    }

    @Test
    void client_secret이_틀리면_서비스_토큰을_주지_않는다() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("user-service", "wrong"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "internal"))
                .andExpect(status().isUnauthorized());
    }

    static JwtRequestPostProcessor userToken() {
        return jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("SCOPE_openid"));
    }

    static JwtRequestPostProcessor serviceToken() {
        return jwt().jwt(j -> j.subject("user-service")).authorities(new SimpleGrantedAuthority("SCOPE_internal"));
    }

    private MockHttpServletRequestBuilder signup() {
        return post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY);
    }

    private MockHttpServletRequestBuilder delete() {
        return post(DELETE).contentType(MediaType.APPLICATION_JSON).content("1");
    }

    private void saveAccount() {
        authRepository.save(Auth.builder()
                .userId(1L)
                .email("victim@gearfirst.com")
                .password("encoded")
                .build());
    }
}
