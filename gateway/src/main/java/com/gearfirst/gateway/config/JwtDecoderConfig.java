package com.gearfirst.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
public class JwtDecoderConfig {
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri) // Auth 서버 공개키 주소. 환경마다 달라서 설정으로 주입
                .build();
    }
}
