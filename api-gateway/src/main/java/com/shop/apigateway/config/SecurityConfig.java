package com.shop.apigateway.config;

import com.shop.apigateway.security.JwtWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Конфигурация безопасности на уровне API Gateway.
 * <p>
 * Здесь:
 * <ul>
 *     <li>Включаем WebFlux security (реактивный стек).</li>
 *     <li>Отключаем stateful-механику (CSRF, formLogin, httpBasic).</li>
 *     <li>Настраиваем, какие маршруты доступны без аутентификации.</li>
 *     <li>Подключаем кастомный {@link JwtWebFilter}, который валидирует JWT
 *     и кладёт аутентификацию в SecurityContext.</li>
 * </ul>
 *
 * <p><b>Важно:</b> {@code RequestIdWebFilter} является {@code GlobalFilter} (Spring Cloud Gateway),
 * а не {@code WebFilter} (WebFlux Security). Поэтому он не должен добавляться в
 * {@link SecurityWebFilterChain} через {@code addFilterAt}. Он будет применяться
 * Gateway автоматически как глобальный фильтр (через {@code @Component}).
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Реактивный JWT-фильтр.
     * <p>
     * Валидирует JWT и, если токен корректный, поднимает Authentication в SecurityContext.
     */
    private final JwtWebFilter jwtWebFilter;

    /**
     * Основная цепочка фильтров WebFlux Security.
     *
     * @param http реактивный билдер настроек безопасности
     * @return настроенная {@link SecurityWebFilterChain} для всего gateway
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .anyExchange().authenticated()
                )
                // JWT — на этапе AUTHENTICATION
                .addFilterAt(jwtWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * Кодировщик паролей для регистрации/логина.
     *
     * @return {@link PasswordEncoder} на основе BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}





