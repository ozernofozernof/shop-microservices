package com.shop.apigateway.filter;

import com.shop.apigateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUserHeaderFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();

        // Хедер только для запросов в order-service
        if (!path.startsWith("/api/orders")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JwtUserHeaderFilter: no Authorization header for path={}", path);
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("JwtUserHeaderFilter: invalid token for path={}", path);
            return chain.filter(exchange);
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        log.info("JwtUserHeaderFilter: extracted username='{}' for path={}", username, path);

        var mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Name", username)
                .build();

        var mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -1; // фильтр пораньше
    }
}

