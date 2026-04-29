package com.settlr.settlr_api.dto.balance;

import java.math.BigDecimal;
import java.util.List;

/**
 * The authenticated user's net balance across ALL their groups.
 */
public record UserBalanceSummaryResponse(
        String userName,
        String email,
        BigDecimal totalNetBalance,
        List<GroupBalanceSummaryResponse> groupBalances
) {}
