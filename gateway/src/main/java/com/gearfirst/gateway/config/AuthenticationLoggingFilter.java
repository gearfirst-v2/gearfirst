package com.gearfirst.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationLoggingFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        return chain.filter(exchange).doOnSuccess(aVoid -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();

            if (status != null && status.value() == 401) {
                log.error(
                        "🔐 401 Unauthorized 발생! URL={}", exchange.getRequest().getURI());
            }

            if (status != null && status.equals(HttpStatus.UNAUTHORIZED)) {

                String path = exchange.getRequest().getURI().toString();
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                String host = exchange.getRequest().getHeaders().getFirst("Host");
                String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
                String forwardedHost = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Host");

                log.error("🔐 [401 Unauthorized Detected]");
                log.error(" - Path           : {}", path);
                log.error(" - Host           : {}", host);
                log.error(" - X-Forwarded-Proto : {}", forwardedProto);
                log.error(" - X-Forwarded-Host  : {}", forwardedHost);
                log.error(
                        " - Authorization  : {}",
                        authHeader != null
                                ? authHeader.substring(0, Math.min(authHeader.length(), 30)) + "..."
                                : "(none)");

                // 추가로 issuer mismatch 의심 시 힌트를 주자
                if (authHeader == null) {
                    log.error("❗ 이유: Authorization 헤더 없음 → 토큰 미포함 요청");
                } else if (authHeader.startsWith("Bearer")) {
                    log.error("❗ 이유: 토큰 존재하지만 검증 실패 → issuer mismatch / 만료 / 손상 가능성");
                } else {
                    log.error("❗ 이유: Authorization 형식 오류");
                }
            }
        });
    }
}
