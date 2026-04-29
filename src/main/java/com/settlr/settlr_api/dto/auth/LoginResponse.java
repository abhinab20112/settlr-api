package com.settlr.settlr_api.dto.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned after a successful login.
 * Use the token as: Authorization: Bearer <token>
 */
public record LoginResponse(
        String token,
        String tokenType,
        UUID userId,
        String email,
        Instant expiresAt    // exact UTC expiry time — e.g. "2026-04-29T09:00:00Z"
) {}
