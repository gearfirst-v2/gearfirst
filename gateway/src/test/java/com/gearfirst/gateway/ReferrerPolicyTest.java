package com.gearfirst.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * GEA-14: Spring Security 기본값 Referrer-Policy: no-referrer가 gateway 뒤 auth 로그인 페이지에도 붙으면,
 * 브라우저는 로그인 폼 POST의 Origin을 "null"로 보내고 auth CORS가 403(Invalid CORS request)으로 막는다.
 * same-origin은 같은 출처에만 출처를 알리고 다른 사이트에는 여전히 아무것도 보내지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReferrerPolicyTest {

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @Test
    @DisplayName("응답의 Referrer-Policy는 same-origin이다 (no-referrer면 로그인 폼 Origin이 null이 된다)")
    void referrerPolicyIsSameOrigin() {
        client.get().uri("/actuator/health").exchange().expectHeader().valueEquals("Referrer-Policy", "same-origin");
    }
}
