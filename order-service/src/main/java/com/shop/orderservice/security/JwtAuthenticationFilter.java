package com.shop.orderservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        System.out.println("FILTER ACTIVE: " + request.getServletPath());

        //1. Пропускаем все запросы на /api/auth**
        String path = request.getServletPath();
        if (path.startsWith("/api/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        //2. Извлекаем токен из заголовка
        String header = request.getHeader("Authorization");
        String token = null;

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        //3. Проверяем токен
//        if (token != null && jwtTokenProvider.validateToken(token)) {
//            String username = jwtTokenProvider.getUsernameFromToken(token);
//            CustomUserDetails userDetails = userDetailsService.loadUserByUsername(username);
//
//            UsernamePasswordAuthenticationToken auth =
//                    new UsernamePasswordAuthenticationToken(
//                            userDetails, null, userDetails.getAuthorities());
//            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
//
//            SecurityContextHolder.getContext().setAuthentication(auth);
//
//        }
        System.out.println("HEADER RAW = " + header);
        System.out.println("TOKEN RAW = " + token);

        boolean valid = token != null && jwtTokenProvider.validateToken(token);
        System.out.println("TOKEN VALID = " + valid);

        if (valid) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            System.out.println("USERNAME = " + username);

            CustomUserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        //4. Продолжаем выполнение цепочки
        filterChain.doFilter(request, response);
    }
}
