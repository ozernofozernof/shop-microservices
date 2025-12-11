package com.shop.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Реактивный JWT-фильтр для API Gateway.
 * <p>
 * Задачи фильтра:
 * <ul>
 *     <li>Пропускать без проверки все запросы к публичным endpoint'ам {@code /api/auth/**}.</li>
 *     <li>Забирать JWT из заголовка {@code Authorization: Bearer ...}.</li>
 *     <li>Валидировать токен через {@link JwtTokenProvider}.</li>
 *     <li>Загружать пользователя через {@link CustomUserDetailsService} и
 *     класть {@link org.springframework.security.core.Authentication} в
 *     реактивный {@link org.springframework.security.core.context.SecurityContext}.</li>
 * </ul>
 * Тем самым вся аутентификация сосредоточена на входе — в gateway, а
 * downstream-сервисы получают уже аутентифицированный запрос.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtWebFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Основная логика фильтра:
     * <ol>
     *     <li>Пропускает без проверки запросы на {@code /api/auth/**}.</li>
     *     <li>Пытается извлечь и провалидировать JWT.</li>
     *     <li>При успешной валидации поднимает аутентификацию в SecurityContext.</li>
     *     <li>При невалидном токене возвращает 401.</li>
     * </ol>
     *
     * @param exchange текущий HTTP-запрос/ответ (reactive)
     * @param chain    оставшаяся цепочка WebFilter'ов
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // /api/auth/** — публичные: регистрация, логин, refresh
        if (path.startsWith("/api/auth")) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            // Без токена: фильтр не аутентифицирует запрос,
            // дальше сработают стандартные правила security (и вернут 401 для защищённых роутов).
            return chain.filter(exchange);
        }

        String token = header.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("JwtWebFilter: invalid token");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        log.info("JwtWebFilter: token valid, username={}", username);

        // CustomUserDetailsService — блокирующий, выносим в boundedElastic
        return Mono.fromCallable(() -> userDetailsService.loadUserByUsername(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userDetails -> {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    // Кладём Authentication в реактивный SecurityContext,
                    // дальше в цепочке его сможет прочитать Spring Security.
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                });
    }
}

