package com.settlr.settlr_api.dto.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full expense response returned after creation.
 */
public record ExpenseResponse(
        UUID id,
        UUID groupId,
        String groupName,
        UUID paidByUserId,
        String paidByName,
        String description,
        BigDecimal amount,
        String currency,
        List<SplitResponse> splits,
        Instant createdDate
) {}
