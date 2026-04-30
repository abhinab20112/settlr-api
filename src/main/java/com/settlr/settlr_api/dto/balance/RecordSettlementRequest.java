package com.settlr.settlr_api.dto.balance;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Payload for POST /api/groups/{groupId}/settle.
 * The payer (fromUser) is the authenticated user (from JWT).
 */
public record RecordSettlementRequest(

        @Email(message = "Payer must be a valid email")
        String fromEmail,

        @NotBlank(message = "Recipient email is required")
        @Email(message = "Recipient must be a valid email")
        String toEmail,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount
) {}
