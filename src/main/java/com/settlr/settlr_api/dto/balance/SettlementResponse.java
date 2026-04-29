package com.settlr.settlr_api.dto.balance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single "A pays B X" settlement instruction.
 * Enriched with user details (name, email) for display.
 */
public record SettlementResponse(
        UUID fromUserId,
        String fromName,
        String fromEmail,
        UUID toUserId,
        String toName,
        String toEmail,
        BigDecimal amount
) {}
