package com.settlr.settlr_api.dto.group;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full group response returned by all three group endpoints.
 */
public record GroupResponse(
        UUID id,
        String name,
        UUID creatorId,
        List<MemberResponse> members,
        Instant createdDate
) {}
