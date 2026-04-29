package com.settlr.settlr_api.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/groups — create a new group.
 */
public record CreateGroupRequest(

        @NotBlank(message = "Group name is required")
        @Size(min = 3, max = 50, message = "Group name must be between 3 and 50 characters")
        String name
) {}
