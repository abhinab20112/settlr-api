package com.settlr.settlr_api.service.impl;

import com.settlr.settlr_api.dto.auth.LoginRequest;
import com.settlr.settlr_api.dto.auth.LoginResponse;
import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.repository.UserRepository;
import com.settlr.settlr_api.security.JwtService;
import com.settlr.settlr_api.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("[LOGIN] Attempting login | email={}", request.email());

        // Delegates to DaoAuthenticationProvider → CustomUserDetailsService → BCrypt verify.
        // Throws BadCredentialsException automatically if password doesn't match.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Credentials verified — load full entity for JWT claims
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.error("[LOGIN] User vanished after authentication | email={}", request.email());
                    return new IllegalStateException("Authenticated user not found in DB");
                });

        String token = jwtService.generateToken(user);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getExpirationMs());

        log.info("[LOGIN] Login successful | userId={} | email={} | expiresAt={}",
                user.getId(), user.getEmail(), expiresAt);

        return new LoginResponse(
                token,
                "Bearer",
                user.getId(),
                user.getEmail(),
                expiresAt
        );
    }
}
