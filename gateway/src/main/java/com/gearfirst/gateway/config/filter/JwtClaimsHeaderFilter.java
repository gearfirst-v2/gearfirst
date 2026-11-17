package com.gearfirst.gateway.config.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtClaimsHeaderFilter implements GlobalFilter, Ordered {
    private final ReactiveJwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange original, GatewayFilterChain chain) {
        // 클라이언트가 보낸 X-User-* 헤더는 믿지 않는다. 항상 지우고, 아래에서 검증된 토큰으로만 다시 채운다
        ServerWebExchange exchange = original.mutate()
                .request(r -> r.headers(h -> h.keySet().removeIf(n -> n.regionMatches(true, 0, "X-User-", 0, 7))))
                .build();

        log.error("🔥 JwtClaimsHeaderFilter ENTER : Host={}, URI={}",
                exchange.getRequest().getHeaders().getFirst("Host"),
                exchange.getRequest().getURI());

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) return chain.filter(exchange);

        String token = auth.substring(7);

        // 1) 검증 전, 무조건 header/payload를 Base64URL로 디코드해 로깅
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String headerJson  = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                log.error("[JWT RAW-HEADER] {}", headerJson);
                log.error("[JWT RAW-PAYLOAD] {}", payloadJson);
            } else {
                log.error("[JWT MALFORMED] token does not have 2 dots");
            }
        } catch (Exception ex) {
            log.error("[JWT PRE-DECODE FAILURE] {}", ex.toString());
        }

        // 2) 실제 검증 (성공하면 헤더 붙여서 다운스트림으로 전달)
        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    log.debug("[JWT Decode OK] headers={}", jwt.getHeaders());
                    log.debug("[JWT Decode OK] claims={}", jwt.getClaims());

                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-User-Id",      jwt.getClaimAsString("sub"))
                            .header("X-User-Name",    b64(jwt.getClaimAsString("name")))
                            .header("X-User-Rank",    b64(jwt.getClaimAsString("rank")))
                            .header("X-User-Region",  b64(jwt.getClaimAsString("region")))
                            .header("X-User-WorkType",b64(jwt.getClaimAsString("work_type")))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(e -> {
                    // 검증 실패 사유도 같이 출력
                    log.error("[JWT VERIFY FAILURE] {}", e.toString());

                    // 디버깅 편의: 요청 메타도 찍기
                    Map<String, Object> attrs = exchange.getAttributes();
                    log.error("Path={}, Query={}, Host={}",
                            exchange.getRequest().getURI().getPath(),
                            exchange.getRequest().getURI().getQuery(),
                            exchange.getRequest().getHeaders().getFirst("Host"));

                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    private static String b64(String s) {
        if (s == null) return "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override public int getOrder() { return -100; }
}
