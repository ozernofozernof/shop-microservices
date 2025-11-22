package com.shop.orderservice.auth;

import com.shop.orderservice.security.JwtTokenProvider;
import com.shop.orderservice.user.Role;
import com.shop.orderservice.user.User;
import com.shop.orderservice.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

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

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        String accessToken = jwtTokenProvider.generateAccessToken(request.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.getUsername());

        return ResponseEntity.ok(new JwtResponse(accessToken, refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            return ResponseEntity.badRequest().build();
        }

        String username = jwtTokenProvider.getUsernameFromToken(request.getRefreshToken());
        String newAccess = jwtTokenProvider.generateAccessToken(username);
        String newRefresh = jwtTokenProvider.generateRefreshToken(username);

        return ResponseEntity.ok(new JwtResponse(newAccess, newRefresh));
    }
}