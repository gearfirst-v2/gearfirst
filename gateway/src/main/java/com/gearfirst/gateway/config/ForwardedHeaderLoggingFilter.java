package com.gearfirst.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class ForwardedHeaderLoggingFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(ForwardedHeaderLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String xForwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        String xForwardedHost = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Host");

        // 추가 로그
        String method = exchange.getRequest().getMethod().toString();
        String path = exchange.getRequest().getURI().getPath();
        String query = exchange.getRequest().getURI().getQuery();
        String remoteAddr = exchange.getRequest().getRemoteAddress().getAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        if (path.startsWith("/actuator/health") || path.startsWith("/notification")) {
            return chain.filter(exchange);
        }

        logger.info("Request: method={}, path={}, query={}, remoteAddr={}", method, path, query, remoteAddr);
        logger.info("X-Forwarded-For: {}", xForwardedFor);
        logger.info("X-Forwarded-Proto: {}", xForwardedProto);
        logger.info("X-Forwarded-Host: {}", xForwardedHost);
        logger.info("Header : {}", exchange.getRequest().getHeaders());
        logger.info("RemoteAddress : {}", exchange.getRequest().getRemoteAddress());
        logger.info("Host : {}", exchange.getRequest().getHeaders().getFirst("Host"));

        return chain.filter(exchange);
    }
}
