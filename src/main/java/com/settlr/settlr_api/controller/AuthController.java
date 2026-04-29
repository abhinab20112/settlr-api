package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.dto.auth.LoginRequest;
import com.settlr.settlr_api.dto.auth.LoginResponse;
import com.settlr.settlr_api.dto.auth.RegisterRequest;
import com.settlr.settlr_api.dto.auth.RegisterResponse;
import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.security.JwtService;
import com.settlr.settlr_api.service.AuthService;
import com.settlr.settlr_api.service.TokenBlacklistService;
import com.settlr.settlr_api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * POST /api/auth/register
     * Registers a new user. Returns 201 Created with safe user fields.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        User saved = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RegisterResponse(saved.getId(), saved.getName(), saved.getEmail()));
    }

    /**
     * POST /api/auth/login
     * Validates credentials and returns a signed JWT (24h expiry).
     * Use the token as: Authorization: Bearer <token>
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/auth/logout
     *
     * Invalidates the current JWT by storing it in Redis with a TTL equal to
     * its remaining lifetime. Subsequent requests with this token will be
     * rejected by JwtAuthenticationFilter (401 Unauthorized).
     *
     * Requires a valid Bearer token in the Authorization header.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing or invalid Authorization header"));
        }

        final String jwt = authHeader.substring(7);

        // Calculate remaining TTL so Redis auto-evicts when the token would have expired
        long remainingTtlMs = jwtService.getRemainingTtlMs(jwt);

        if (remainingTtlMs <= 0) {
            return ResponseEntity.ok(Map.of("message", "Token already expired — no action needed"));
        }

        tokenBlacklistService.blacklist(jwt, remainingTtlMs);

        String email = jwtService.extractEmail(jwt);
        log.info("[AUTH] User logged out | email={} | tokenTtlRemainingMs={}", email, remainingTtlMs);

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully. Token has been invalidated.",
                "tokenBlacklistedForMs", String.valueOf(remainingTtlMs)
        ));
    }
}
