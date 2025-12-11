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
 *     и обогащает запрос данными пользователя перед проксированием в микросервисы.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Фильтр, который:
     * <ul>
     *     <li>Считывает JWT из заголовка Authorization.</li>
     *     <li>Проверяет подпись и срок действия токена.</li>
     *     <li>Кладёт данные пользователя в SecurityContext.</li>
     *     <li>Прокидывает username дальше через кастомный заголовок
     *     (например, {@code X-User-Name}) в order-service.</li>
     * </ul>
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
                // Gateway — stateless, работаем только по JWT
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                // Правила доступа к маршрутам
                .authorizeExchange(exchanges -> exchanges
                        // Регистрация/логин — публичные
                        .pathMatchers("/api/auth/**").permitAll()
                        // Создание пользователя — тоже доступно без токена
                        .pathMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // Всё остальное — только для аутентифицированных
                        .anyExchange().authenticated()
                )

                // Подключаем наш JWT-фильтр на этап аутентификации
                .addFilterAt(jwtWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .build();
    }

    /**
     * Кодировщик паролей для работы с пользователями (регистрация/логин).
     * <p>
     * Используем BCrypt как де-факто стандарт для хэширования паролей.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}




