package com.gearfirst.user_be.user.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * auth의 signup, delete를 부를 때 쓰는 서비스 토큰을 client credentials로 받아 만료 전까지 재사용한다 (GEA-13).
 * user에는 Spring Security가 없어서 oauth2-client 대신 직접 받는다. 넣으면 user API 전체에 기본 로그인이 걸린다.
 */
@Component
public class AuthServiceTokenProvider {

    static final String CLIENT_ID = "user-service";
    // 만료 직전 토큰을 보내 401을 받지 않도록 조금 일찍 새로 받는다
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String clientSecret;

    private String token;
    private Instant expiresAt = Instant.MIN;

    public AuthServiceTokenProvider(
            RestClient.Builder builder,
            @Value("${auth-service.url}") String authServiceUrl,
            @Value("${auth-service.client-secret}") String clientSecret) {
        this.restClient = builder.baseUrl(authServiceUrl).build();
        this.clientSecret = clientSecret;
    }

    public synchronized String getToken() {
        if (token == null || !Instant.now().isBefore(expiresAt)) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("scope", "internal");
            TokenResponse response = restClient
                    .post()
                    .uri("/oauth2/token")
                    .headers(h -> h.setBasicAuth(CLIENT_ID, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            token = response.accessToken();
            expiresAt = Instant.now().plusSeconds(response.expiresIn()).minus(EXPIRY_MARGIN);
        }
        return token;
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {}
}
