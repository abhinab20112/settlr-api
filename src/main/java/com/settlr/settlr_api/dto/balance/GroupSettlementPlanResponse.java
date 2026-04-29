package com.settlr.settlr_api.dto.balance;

import java.util.List;
import java.util.UUID;

/**
 * Full settlement plan for a group — the minimum payments needed
 * to clear all debts.
 */
public record GroupSettlementPlanResponse(
        UUID groupId,
        String groupName,
        int transactionCount,
        List<SettlementResponse> transactions
) {}
