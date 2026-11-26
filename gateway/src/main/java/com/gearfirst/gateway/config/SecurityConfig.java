package com.gearfirst.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Autowired
    private JwtGatewayAuthLogger jwtGatewayAuthLogger;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
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
