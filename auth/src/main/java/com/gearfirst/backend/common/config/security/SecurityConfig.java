package com.gearfirst.backend.common.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public RequestCache requestCacheBean() { // 공용 Bean으로 등록
        return new CustomRequestCache();
    }

    /**
     * Authorization Server용 SecurityFilterChain
     * (OAuth2.0 endpoints: /oauth2/authorize(클라이언트가 인가 코드 요청), /oauth2/token(accessToken 교환), /oauth2/jwks(공개키제공) 등)
     */
    @Bean
    @Order(1) // OAuth2 관련 엔드포인트 우선 처리
    public SecurityFilterChain authServerSecurityFilterChain(
            HttpSecurity http, RegisteredClientRepository registeredClientRepository) throws Exception {

        // Authorization Server 전용 Configurer 생성
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http.requestCache(c -> c.requestCache(requestCacheBean()))

                // .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .securityMatcher(endpointsMatcher)
                // .with(authorizationServerConfigurer, Customizer.withDefaults())
                .with(
                        authorizationServerConfigurer,
                        (authorizationServer) -> authorizationServer.oidc(Customizer.withDefaults()) // OIDC 켜기
                        )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/.well-known/openid-configuration",
                                "/.well-known/jwks.json",
                                "/.well-known/acme-challenge/**")
                        .permitAll()
                        .requestMatchers("/oauth2/jwks")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));

        http.addFilterAfter(new OAuth2DebugFilter(), SecurityContextHolderFilter.class);

        return http.build();
    }

    /**
     * 일반 로그인 / 폼 처리용 SecurityFilterChain
     */
    @Bean
    @Order(2) // 일반 요청은 두 번째 체인으로 처리
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {

        http.addFilterAfter(
                        (request, response, chain) -> {
                            var auth = SecurityContextHolder.getContext().getAuthentication();
                            log.debug(" [Auth Filter] Principal: " + (auth != null ? auth.getName() : "null"));
                            chain.doFilter(request, response);
                        },
                        UsernamePasswordAuthenticationFilter.class)
                .requestCache(c -> c.requestCache(requestCacheBean()))
                .cors(Customizer.withDefaults())
                .httpBasic(basicConfigurer -> basicConfigurer.disable())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                // Swagger 관련 경로 전체 허용
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                // 일반 공개 경로
                                "/login",
                                "/error",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/.well-known/**",
                                "/auth/actuator/health/**", // liveness/readiness 모두 허용
                                "/actuator/health/**", // liveness/readiness 모두 허용
                                "/auth/actuator/**",
                                "/actuator/info",
                                // 비밀번호를 잊은 사람이 쓰는 API라 토큰 없이 열어 둔다. 남의 비밀번호를 바꾸는 문제는 C6
                                "/auth/api/v1/auth/regenerate-temp-password", // 임시비밀번호 발급
                                "/api/v1/auth/regenerate-temp-password",
                                "/.well-known/acme-challenge/**")
                        .permitAll()
                        // user 서비스가 client credentials로 받은 서비스 토큰만 허용 (C2, GEA-13)
                        .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/delete")
                        .hasAuthority("SCOPE_internal")
                        // 로그인한 사람의 JWT만 허용. 서비스 토큰과 폼 로그인 세션(CSRF 꺼짐)은 거부 (C3, GEA-13)
                        .requestMatchers("/api/v1/auth/change-password")
                        .access((authentication, context) ->
                                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken token
                                        && token.getAuthorities().stream()
                                                .noneMatch(a -> a.getAuthority().equals("SCOPE_internal"))))
                        .anyRequest()
                        .authenticated())
                // auth가 직접 JWT를 검증한다. gateway는 /auth/**를 검증 없이 통과시키기 때문 (ADR 0004)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new BearerTokenAuthenticationEntryPoint(), new AntPathRequestMatcher("/api/**")))
                .formLogin(form -> form.loginPage("/login") // 커스텀 로그인 페이지
                        .loginProcessingUrl("/login") // 로그인 POST 엔드포인트
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .failureUrl("/login?error=true")
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "SESSION", "remember-me")
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            // 로그아웃 요청 로그
                            log.info(" [LOGOUT] 로그아웃 요청 감지");

                            // 현재 세션 정보 확인
                            HttpSession session = request.getSession(false);
                            if (session == null) {
                                log.info(" 세션이 이미 무효화됨 (session == null)");
                            } else {
                                log.info(" 세션 아직 살아있음, ID={}", session.getId());
                            }

                            // 클라이언트 쿠키 정보 확인
                            if (request.getCookies() != null) {
                                Arrays.stream(request.getCookies())
                                        .forEach(c -> log.info(" [쿠키] {} = {}", c.getName(), c.getValue()));
                            } else {
                                log.info(" 요청에 쿠키가 없음");
                            }

                            // 응답 쿠키 확인 (Set-Cookie 헤더 직접 추가 후 확인용)
                            response.addCookie(new Cookie("logout-log", "done"));
                            log.info(" 로그아웃 처리 완료, Set-Cookie 헤더로 JSESSIONID 만료 예정");

                            response.setStatus(HttpServletResponse.SC_OK);
                        }));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // iOS나 Android의 OAuth 2.0 PKCE 플로우는 CORS가 적용되지 않음
    // 네이티브 앱이 자체적으로 HTTP 요청을 보내는 클라이언트이기 때문
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", // 로컬 개발용
                "https://gearfirst-fe.vercel.app" // 프론트 배포용
                ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ForwardedHeaderFilter());
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
    }
}
