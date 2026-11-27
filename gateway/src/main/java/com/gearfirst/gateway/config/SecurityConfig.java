package com.gearfirst.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Autowired
    private JwtGatewayAuthLogger jwtGatewayAuthLogger;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                // 기본값 no-referrer면 브라우저가 auth 로그인 폼 POST의 Origin을 "null"로 보내 auth CORS가 403으로 막는다(GEA-14).
                // same-origin도 다른 사이트에는 주소를 보내지 않는다. auth CORS 정리는 C4(로그인 폼 CSRF)에서 한다
                .headers(headers -> headers.referrerPolicy(
                        referrer -> referrer.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeExchange(auth -> auth.pathMatchers(
                                // auth는 로그인 화면·토큰 발급·JWKS처럼 토큰을 받기 전에 쓰는 곳이라 열어 두고,
                                // auth 안의 보안 설정이 각 엔드포인트를 직접 지킨다
                                "/auth/**", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // user, order 등 나머지 서비스는 gateway에서 토큰을 검증한 요청만 보낸다
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}) // JwtDecoderConfig의 jwtDecoder 빈을 사용
                        .authenticationEntryPoint(jwtGatewayAuthLogger))
                .build();
    }

    //        @Bean
    //        public CorsConfigurationSource corsConfigurationSource() {
    //            CorsConfiguration config = new CorsConfiguration();
    //            config.setAllowedOrigins(List.of("https://frontend.gearfirst.com", "http://localhost:5173"));
    //            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    //            config.setAllowedHeaders(List.of("*"));
    //            config.setAllowCredentials(true);
    //            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //            source.registerCorsConfiguration("/**", config);
    //            return source;
    //        }
}
