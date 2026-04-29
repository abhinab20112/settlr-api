package com.settlr.settlr_api.dto.group;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for POST /api/groups/{groupId}/members — add a user by email.
 */
public record AddMemberRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email
) {}
