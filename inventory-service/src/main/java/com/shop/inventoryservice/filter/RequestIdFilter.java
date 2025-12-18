package com.shop.inventoryservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP-фильтр (Spring MVC), обеспечивающий сквозной requestId для логов.
 *
 * <p>Поведение:
 * <ul>
 *   <li>Читает {@code X-Request-Id} из входящего запроса.</li>
 *   <li>Если заголовок отсутствует/пустой — генерирует UUID.</li>
 *   <li>Кладёт значение в MDC под ключом {@code requestId}.</li>
 *   <li>Возвращает {@code X-Request-Id} в ответе.</li>
 * </ul>
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    /** Имя заголовка, используемое во всех микросервисах. */
    public static final String HEADER = "X-Request-Id";

    /** Ключ MDC, который используется в logging.pattern: [%X{requestId}]. */
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_KEY, requestId);
        try {
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}


