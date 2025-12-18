package com.shop.apigateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Глобальный фильтр API Gateway, обеспечивающий сквозной requestId.
 *
 * <p>Функции:
 * <ul>
 *   <li>Берёт {@code X-Request-Id} из входящего запроса или генерирует UUID.</li>
 *   <li>Прокидывает {@code X-Request-Id} дальше в микросервисы (через mutate request).</li>
 *   <li>Добавляет {@code X-Request-Id} в ответ (ставим сразу, пока headers ещё mutable).</li>
 *   <li>Кладёт requestId в MDC под ключом {@code requestId} для логов.</li>
 * </ul>
 */
@Component
public class RequestIdWebFilter implements GlobalFilter, Ordered {

    /** Имя заголовка, используемое во всех микросервисах. */
    public static final String HEADER = "X-Request-Id";

    /** Ключ MDC, который используется в logging.pattern: [%X{requestId}]. */
    public static final String MDC_KEY = "requestId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = resolveRequestId(exchange.getRequest().getHeaders());

        ServerHttpRequest requestWithRequestId = exchange.getRequest()
                .mutate()
                .headers(httpHeaders -> httpHeaders.set(HEADER, requestId))
                .build();

        ServerWebExchange exchangeWithRequestId = exchange.mutate()
                .request(requestWithRequestId)
                .build();

        // ВАЖНО: ставим header сразу, пока response headers ещё не стали ReadOnly
        exchangeWithRequestId.getResponse().getHeaders().set(HEADER, requestId);

        return chain.filter(exchangeWithRequestId)
                .doFirst(() -> MDC.put(MDC_KEY, requestId))
                .doFinally(signalType -> MDC.remove(MDC_KEY));
    }

    /**
     * Возвращает requestId из заголовка или генерирует новый UUID.
     *
     * @param headers HTTP заголовки входящего запроса
     * @return непустой requestId
     */
    private String resolveRequestId(HttpHeaders headers) {
        return Optional.ofNullable(headers.getFirst(HEADER))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());
    }
}

