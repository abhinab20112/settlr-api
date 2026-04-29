
package com.settlr.settlr_api.service;

import com.settlr.settlr_api.dto.balance.*;

import java.util.UUID;

public interface BalanceService {

    /**
     * Returns the net balance of every member in a group.
     */
    GroupBalanceSummaryResponse getGroupBalances(UUID groupId, String userEmail);

    /**
     * Returns the authenticated user's net balance across ALL their groups.
     */
    UserBalanceSummaryResponse getUserBalancesAcrossGroups(String userEmail);

    /**
     * Calculates the minimum list of payments needed to clear all debts in a group.
     * This is READ-ONLY — it does NOT record any payments.
     */
    GroupSettlementPlanResponse getSettlementPlan(UUID groupId, String userEmail);

    /**
     * Records an actual payment from the authenticated user to another user in a group.
     * Both the Settlement record and the UserBalance update happen in a single
     * @Transactional method — if the balance update fails, the settlement record
     * is also rolled back.
     *
     * @param groupId    the group context
     * @param request    recipient email + amount
     * @param payerEmail authenticated user's email (from JWT)
     */
    RecordSettlementResponse recordSettlement(UUID groupId, RecordSettlementRequest request, String payerEmail);
}
