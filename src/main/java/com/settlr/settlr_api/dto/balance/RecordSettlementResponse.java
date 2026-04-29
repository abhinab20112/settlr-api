
package com.settlr.settlr_api.dto.balance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Confirmation returned after recording a settlement.
 */
public record RecordSettlementResponse(
        UUID settlementId,
        UUID groupId,
        String groupName,
        UUID fromUserId,
        String fromName,
        UUID toUserId,
        String toName,
        BigDecimal amount,
        Instant createdDate
) {}
