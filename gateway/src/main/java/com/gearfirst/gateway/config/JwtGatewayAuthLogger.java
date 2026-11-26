package com.gearfirst.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayAuthLogger implements ServerAuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtGatewayAuthLogger.class);

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        log.error("===== GATEWAY JWT FAILURE =====");
        log.error("Request URI: {}", exchange.getRequest().getURI());
        log.error("Host: {}", exchange.getRequest().getHeaders().getFirst("Host"));
        log.error("Authorization: {}", exchange.getRequest().getHeaders().getFirst("Authorization"));
        log.error("Error: {}", ex.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
