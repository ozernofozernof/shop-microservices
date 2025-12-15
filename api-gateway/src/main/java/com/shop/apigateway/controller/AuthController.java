package com.shop.apigateway.controller;

import com.shop.apigateway.dto.JwtResponse;
import com.shop.apigateway.dto.LoginRequest;
import com.shop.apigateway.dto.RefreshTokenRequest;
import com.shop.apigateway.dto.RegisterRequest;
import com.shop.apigateway.entity.Role;
import com.shop.apigateway.entity.User;
import com.shop.apigateway.repository.UserRepository;
import com.shop.apigateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * REST-контроллер, отвечающий за аутентификацию и выдачу JWT-токенов.
 * <p>
 * На уровне API Gateway здесь реализованы:
 * <ul>
 *     <li>регистрация нового пользователя ({@code /api/auth/register});</li>
 *     <li>логин по логину и паролю ({@code /api/auth/login});</li>
 *     <li>обновление пары токенов по refresh-токену ({@code /api/auth/refresh}).</li>
 * </ul>
 * <p>
 * Все операции работают с локальной БД пользователя (таблица {@code users}) в gateway
 * и используют {@link JwtTokenProvider} для генерации и валидации токенов.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Регистрация нового пользователя.
     * <p>
     * Шаги:
     * <ol>
     *     <li>Проверяем, что логин и email ещё не заняты.</li>
     *     <li>Хешируем пароль с помощью {@link PasswordEncoder}.</li>
     *     <li>Сохраняем пользователя с ролью {@link Role#ROLE_USER}.</li>
     *     <li>Сразу выдаём пару токенов (access + refresh).</li>
     * </ol>
     *
     * @param request DTO с данными регистрации (username, email, password)
     * @return
     * <ul>
     *     <li>400, если логин или email уже используются;</li>
     *     <li>200 + {@link JwtResponse}, если регистрация успешна.</li>
     * </ul>
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already in use");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.ROLE_USER)
                .build();

        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return ResponseEntity.ok(new JwtResponse(accessToken, refreshToken));
    }

    /**
     * Логин пользователя по логину и паролю.
     * <p>
     * Валидация выполняется вручную:
     * <ul>
     *     <li>ищем пользователя по username;</li>
     *     <li>сравниваем сырой пароль с хешем через {@link PasswordEncoder#matches}.</li>
     * </ul>
     * При успехе выдаём новую пару (access + refresh) токенов.
     *
     * @param request DTO с логином и паролем
     * @return 200 + {@link JwtResponse} при успешной аутентификации
     * @throws RuntimeException если логин или пароль неверные
     *                          (обрабатывается глобальным обработчиком/по умолчанию фреймворком)
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request) {
        // руками проверяем логин/пароль
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return ResponseEntity.ok(new JwtResponse(accessToken, refreshToken));
    }

    /**
     * Обновление пары JWT-токенов по действующему refresh-токену.
     * <p>
     * Шаги:
     * <ol>
     *     <li>Проверяем подпись и срок жизни переданного токена.</li>
     *     <li>Убеждаемся, что в claim {@code type} указано {@code "refresh"}.</li>
     *     <li>Извлекаем username из токена.</li>
     *     <li>Генерируем новую пару access + refresh токенов.</li>
     * </ol>
     *
     * @param request DTO с полем {@code refreshToken}
     * @return
     * <ul>
     *     <li>400, если токен некорректен или не является refresh;</li>
     *     <li>200 + {@link JwtResponse} с новой парой токенов при успехе.</li>
     * </ul>
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken) ||
                !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return ResponseEntity.badRequest().body(null);
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        String newAccess = jwtTokenProvider.generateAccessToken(username);
        String newRefresh = jwtTokenProvider.generateRefreshToken(username);

        return ResponseEntity.ok(new JwtResponse(newAccess, newRefresh));
    }
}
