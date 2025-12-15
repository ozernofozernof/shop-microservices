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

/**
 * Глобальный фильтр Spring Cloud Gateway, который пробрасывает username
 * из JWT в downstream-сервисы через заголовок {@code X-User-Name}.
 * <p>
 * Основная идея:
 * <ul>
 *     <li>Фильтр срабатывает на все запросы, но реально обрабатывает только те,
 *         что идут на {@code /api/orders/**} (order-service).</li>
 *     <li>Извлекает JWT из {@code Authorization: Bearer ...}.</li>
 *     <li>Валидирует токен через {@link JwtTokenProvider}.</li>
 *     <li>Если токен валиден — достаёт username и добавляет заголовок {@code X-User-Name}.</li>
 *     <li>Если токена нет или он невалиден — просто логирует и ничего не подменяет.</li>
 * </ul>
 * Таким образом, сам order-service не знает про JWT, а получает уже “готовый”
 * username в заголовке и работает с ним как с доверенным источником.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUserHeaderFilter implements GlobalFilter, Ordered {

    /**
     * Имя заголовка, в котором мы пробрасываем имя пользователя
     * в order-service.
     */
    public static final String USERNAME_HEADER = "X-User-Name";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Основная логика фильтра:
     * <ol>
     *     <li>Проверяем путь: если это не {@code /api/orders/**} — просто пропускаем запрос дальше.</li>
     *     <li>Пытаемся считать {@code Authorization} и извлечь оттуда JWT.</li>
     *     <li>Если токен есть и он валиден — достаём username и добавляем его в заголовок.</li>
     *     <li>В любом случае продолжаем цепочку фильтров (фильтр не отвечает сам).</li>
     * </ol>
     *
     * @param exchange текущий запрос/ответ
     * @param chain    оставшаяся цепочка фильтров gateway
     * @return реактивное завершение цепочки фильтров
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();

        // Пробрасываем username только для запросов в order-service
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
            // Не ломаем запрос, просто не добавляем заголовок
            return chain.filter(exchange);
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        log.info("JwtUserHeaderFilter: extracted username='{}' for path={}", username, path);

        var mutatedRequest = exchange.getRequest()
                .mutate()
                .header(USERNAME_HEADER, username)
                .build();

        var mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange);
    }

    /**
     * Приоритет фильтра в цепочке Gateway.
     * <p>
     * Отрицательное значение означает, что фильтр будет выполнен
     * раньше фильтров с приоритетом по умолчанию (0).
     *
     * @return порядок исполнения фильтра
     */
    @Override
    public int getOrder() {
        return -1; // выполняем фильтр пораньше
    }
}

