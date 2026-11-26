package com.gearfirst.user_be.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** GEA-13: user가 auth의 signup, delete를 부를 때 client credentials 서비스 토큰을 붙인다. */
class AuthServiceTokenProviderTest {

    private static final String TOKEN_URL = "http://auth/auth/oauth2/token";
    private static final String BASIC =
            "Basic " + Base64.getEncoder().encodeToString("user-service:test-secret".getBytes(StandardCharsets.UTF_8));

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void client_credentials로_토큰을_받아_만료_전까지_재사용한다() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andExpect(content()
                        .formDataContains(java.util.Map.of("grant_type", "client_credentials", "scope", "internal")))
                .andRespond(tokenResponse("token-1", 300));
        var provider = provider();

        assertThat(provider.getToken()).isEqualTo("token-1");
        assertThat(provider.getToken()).isEqualTo("token-1");

        server.verify(); // 토큰 요청은 한 번뿐
    }

    @Test
    void 만료가_가까우면_새로_받는다() {
        // 만료 10초 < 여유 30초 → 받자마자 만료로 보고 다음 호출에서 새로 받는다
        server.expect(times(2), requestTo(TOKEN_URL)).andRespond(tokenResponse("short", 10));
        var provider = provider();

        provider.getToken();
        provider.getToken();

        server.verify();
    }

    @Test
    void AuthClient_요청에_Bearer_서비스_토큰을_붙인다() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(tokenResponse("token-1", 300));
        var template = new RequestTemplate();

        new AuthClientConfig().serviceTokenInterceptor(provider()).apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer token-1");
    }

    private AuthServiceTokenProvider provider() {
        return new AuthServiceTokenProvider(builder, "http://auth/auth", "test-secret");
    }

    private static org.springframework.test.web.client.ResponseCreator tokenResponse(String token, int expiresIn) {
        return withSuccess(
                """
                {"access_token": "%s", "token_type": "Bearer", "expires_in": %d, "scope": "internal"}
                """
                        .formatted(token, expiresIn),
                MediaType.APPLICATION_JSON);
    }
}
