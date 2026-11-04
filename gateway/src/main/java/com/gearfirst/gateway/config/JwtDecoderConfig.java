package com.gearfirst.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
public class JwtDecoderConfig {
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder
                //.withJwkSetUri("http://auth-service:8080/oauth2/jwks") //  내부 DNS 주소
                .withJwkSetUri("http://34.120.215.23/auth/oauth2/jwks") // Auth 서버 주소
                .build();
    }
}
