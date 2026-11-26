package com.gearfirst.user_be.user.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/** AuthClient 전용 Feign 설정. @Configuration을 붙이면 모든 Feign 클라이언트에 적용돼서 붙이지 않는다. */
public class AuthClientConfig {

    @Bean
    RequestInterceptor serviceTokenInterceptor(AuthServiceTokenProvider tokenProvider) {
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken());
    }
}
