package com.settlr.settlr_api.dto.expense;

import java.math.BigDecimal;
import java.util.UUID;

/** How much one user owes for this expense. */
public record SplitResponse(
        UUID userId,
        String name,
        String email,
        BigDecimal share
) {}
