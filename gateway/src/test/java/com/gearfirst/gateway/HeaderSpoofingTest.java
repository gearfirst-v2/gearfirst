package com.gearfirst.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * GEA-8: 클라이언트가 X-User-* 헤더를 직접 보내 다른 사용자나 본사 권한으로 행세하는 공격.
 * user·order는 gateway가 넘겨준 X-User-* 헤더만 믿으므로, gateway가 막아야 한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // 실제 서비스 대신 아래 EchoController로 보내, 다운스트림이 받은 헤더를 확인한다
            "spring.cloud.gateway.server.webflux.routes[0].id=test-public",
            "spring.cloud.gateway.server.webflux.routes[0].uri=forward:/test-echo",
            "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/auth/**",
            "spring.cloud.gateway.server.webflux.routes[1].id=test-protected",
            "spring.cloud.gateway.server.webflux.routes[1].uri=forward:/test-echo",
            "spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/echo-protected/**"
        })
@Import(HeaderSpoofingTest.EchoController.class)
class HeaderSpoofingTest {

    private static final String HQ = base64("본사");

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willReturn(Mono.error(new BadJwtException("invalid")));
        given(jwtDecoder.decode("valid-token"))
                .willReturn(Mono.just(Jwt.withTokenValue("valid-token")
                        .header("alg", "RS256")
                        .subject("1")
                        .claim("name", "본사테스트")
                        .claim("rank", "팀장")
                        .claim("region", "서울")
                        .claim("work_type", "본사")
                        .build()));
    }

    @Test
    @DisplayName("토큰 없이 위조 헤더로 user /me를 부르면 401 (A1)")
    void spoofedHeadersWithoutTokenToUser() {
        client.get()
                .uri("/user/api/v1/me")
                .header("X-User-Id", "1")
                .header("X-User-WorkType", HQ)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("토큰 없이 전체 사용자 목록을 부르면 401 (A2)")
    void userListWithoutToken() {
        client.get().uri("/user/api/v1/getAllUser").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("토큰 없이 위조 헤더(본사)로 본사 전용 발주 목록을 부르면 401 (A1)")
    void spoofedHeadersWithoutTokenToOrder() {
        client.get()
                .uri("/order/api/v1/purchase-orders/head/orders/pending")
                .header("X-User-Id", "1")
                .header("X-User-WorkType", HQ)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("공개 경로로 보낸 위조 X-User-* 헤더는 다운스트림에 전달되지 않는다")
    void spoofedHeadersAreStrippedOnPublicPath() {
        Map<String, String> received = client.get()
                .uri("/auth/login")
                .header("X-User-Id", "1")
                .header("X-User-WorkType", HQ)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EchoController.HEADERS)
                .returnResult()
                .getResponseBody();

        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("토큰이 있으면 X-User-* 헤더는 토큰 클레임으로만 채워지고, 위조한 값은 남지 않는다")
    void headersComeOnlyFromToken() {
        Map<String, String> received = client.get()
                .uri("/echo-protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN") // 필터가 채우지 않는 이름도 지워져야 한다
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EchoController.HEADERS)
                .returnResult()
                .getResponseBody();

        assertThat(received)
                .containsEntry("x-user-id", "1")
                .containsEntry("x-user-worktype", HQ)
                .doesNotContainKey("x-user-role");
    }

    @Test
    @DisplayName("잘못된 토큰이면 401")
    void invalidToken() {
        client.get()
                .uri("/echo-protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer forged")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    private static String base64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** gateway가 다운스트림으로 넘긴 X-User-* 헤더를 그대로 돌려준다. */
    @TestConfiguration
    @RestController
    static class EchoController {
        static final org.springframework.core.ParameterizedTypeReference<Map<String, String>> HEADERS =
                new org.springframework.core.ParameterizedTypeReference<>() {};

        @GetMapping("/test-echo")
        Map<String, String> echo(@RequestHeader HttpHeaders headers) {
            Map<String, String> userHeaders = new TreeMap<>();
            headers.forEach((name, values) -> {
                if (name.toLowerCase().startsWith("x-user-")) {
                    userHeaders.put(name.toLowerCase(), values.get(0));
                }
            });
            return userHeaders;
        }
    }
}
