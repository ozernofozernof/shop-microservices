package com.shop.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Пробрасывает username из SecurityContext (уже после JWT-аутентификации)
 * в downstream через заголовок X-User-Name.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUserHeaderFilter implements GlobalFilter, Ordered {

    public static final String USERNAME_HEADER = "X-User-Name";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // только туда, где реально нужен username
        if (!(path.startsWith("/api/orders") || path.startsWith("/api/users"))) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .ofType(Authentication.class)
                .map(Authentication::getName)
                .filter(u -> !u.isBlank())
                .flatMap(username -> {
                    log.info("JwtUserHeaderFilter: add X-User-Name='{}' for path={}", username, path);

                    var mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header(USERNAME_HEADER, username)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}


