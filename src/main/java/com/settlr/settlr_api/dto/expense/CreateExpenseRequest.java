package com.settlr.settlr_api.dto.expense;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload for POST /api/groups/{groupId}/expenses.
 *
 * The payer is inferred from the JWT (authenticated user).
 * Participants are identified by email. If the list is empty,
 * the expense is split equally among ALL group members.
 */
public record CreateExpenseRequest(

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. INR, USD)")
        String currency,

        com.settlr.settlr_api.entity.Category category,

        String customCategory,

        /**
         * Emails of participants who share this expense.
         * If null or empty → split among ALL group members.
         */
        List<@Email(message = "Each participant must be a valid email") String> participantEmails
) {}
