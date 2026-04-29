package com.settlr.settlr_api.dto.balance;

import java.util.List;
import java.util.UUID;

/**
 * Full balance summary for a group — wraps the per-member net balances.
 */
public record GroupBalanceSummaryResponse(
        UUID groupId,
        String groupName,
        List<MemberBalanceResponse> memberBalances
) {}
