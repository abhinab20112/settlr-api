package com.settlr.settlr_api.dto.group;

import java.util.UUID;

/** Slim user projection returned inside a GroupResponse. */
public record MemberResponse(
        UUID id,
        String name,
        String email
) {}
