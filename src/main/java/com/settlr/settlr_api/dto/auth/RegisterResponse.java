package com.settlr.settlr_api.dto.auth;

import java.util.UUID;

/**
 * Response payload returned after a successful registration.
 */
public record RegisterResponse(
        UUID id,
        String name,
        String email
) {}
