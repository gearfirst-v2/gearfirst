package com.gearfirst.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Autowired
    private JwtGatewayAuthLogger jwtGatewayAuthLogger;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .cors(cors -> cors.disable())
                    .authorizeExchange(auth -> auth
                            .pathMatchers(
                                    "/swagger-ui/**",
                                    "/v3/api-docs/**",
                                    "/actuator/**",
                                    "/auth/**",
                                    "/user/**",
                                    "/inventory/**",
                                    "/receipt/**", "/order/**",
                                    "/warehouse/**",
                                    "/notification/**",
                                    "/.well-known/acme-challenge/**"
                            ).permitAll()
                            .anyExchange().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt
                                            .jwkSetUri("http://34.120.215.23/auth/oauth2/jwks")
                                    //.jwkSetUri("https://gearfirst-auth-mobile.duckdns.org/auth/oauth2/jwks")
                            )
                            .authenticationEntryPoint(jwtGatewayAuthLogger)
                    )
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
