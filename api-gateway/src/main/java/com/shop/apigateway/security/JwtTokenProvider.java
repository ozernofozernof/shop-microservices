package com.shop.apigateway.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * Провайдер JWT-токенов для API Gateway.
 * <p>
 * Отвечает за:
 * <ul>
 *     <li>Генерацию access- и refresh-токенов.</li>
 *     <li>Подпись токенов HMAC-ключом на основе секрета из конфигурации.</li>
 *     <li>Валидацию токена (целостность + срок жизни).</li>
 *     <li>Извлечение имени пользователя (subject) и типа токена.</li>
 * </ul>
 * <p>
 * Секрет берётся из свойства {@code jwt.secret} и должен быть достаточно длинным
 * для алгоритма HS256 (как минимум 32 байта).
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Время жизни access-токена: 15 минут.
     */
    private final long accessTokenValidityMs = 1000 * 60 * 15;

    /**
     * Время жизни refresh-токена: 7 дней.
     */
    private final long refreshTokenValidityMs = 1000L * 60 * 60 * 24 * 7;

    /**
     * Строит криптографический ключ для подписи токенов
     * на основе симметричного секрета из конфигурации.
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Генерирует access-токен для указанного пользователя.
     * <p>
     * Особенности:
     * <ul>
     *     <li>subject = username;</li>
     *     <li>claim {@code type} = "access";</li>
     *     <li>короткий срок жизни (15 минут).</li>
     * </ul>
     *
     * @param username имя пользователя (логин), которое кладём в subject
     * @return подписанный JWT-строка
     */
    public String generateAccessToken(String username) {
        return generateToken(username, accessTokenValidityMs, "access");
    }

    /**
     * Генерирует refresh-токен для указанного пользователя.
     * <p>
     * Особенности:
     * <ul>
     *     <li>subject = username;</li>
     *     <li>claim {@code type} = "refresh";</li>
     *     <li>длинный срок жизни (7 дней).</li>
     * </ul>
     *
     * @param username имя пользователя (логин), которое кладём в subject
     * @return подписанный JWT-строка
     */
    public String generateRefreshToken(String username) {
        return generateToken(username, refreshTokenValidityMs, "refresh");
    }

    /**
     * Общий метод генерации JWT, используется и для access, и для refresh токенов.
     *
     * @param username   subject токена
     * @param validityMs время жизни токена в миллисекундах
     * @param type       тип токена, кладётся в claim {@code type} ("access" или "refresh")
     * @return подписанный JWT-строка
     */
    private String generateToken(String username, long validityMs, String type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .setSubject(username)
                .addClaims(Map.of("type", type))
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Извлекает имя пользователя (subject) из валидного JWT.
     *
     * @param token строка JWT
     * @return имя пользователя (subject) из токена
     * @throws JwtException если токен повреждён или просрочен
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Проверяет, что данный токен является refresh-токеном.
     * <p>
     * Условие: claim {@code type} == "refresh".
     *
     * @param token JWT-строка
     * @return {@code true}, если токен валидный и его тип = "refresh",
     *         {@code false} — при любой ошибке парсинга/валидации.
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return "refresh".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Валидирует токен:
     * <ul>
     *     <li> проверка подписи;</li>
     *     <li> проверка формата;</li>
     *     <li> проверка срока жизни (expiration).</li>
     * </ul>
     *
     * @param token JWT-строка
     * @return {@code true}, если токен корректен и не просрочен;
     *         {@code false} при любой ошибке.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

