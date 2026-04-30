
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
     * The settlement is created in PENDING status. Balances are NOT updated yet.
     *
     * @param groupId    the group context
     * @param request    recipient email + amount
     * @param payerEmail authenticated user's email (from JWT)
     */
    RecordSettlementResponse recordSettlement(UUID groupId, RecordSettlementRequest request, String payerEmail);

    /**
     * Resolves a pending settlement (confirm or reject).
     * Only the recipient can resolve it. If confirmed, balances are updated.
     *
     * @param groupId      the group context
     * @param settlementId the settlement to resolve
     * @param request      confirm flag
     * @param userEmail    authenticated user's email (must be the recipient)
     */
    RecordSettlementResponse resolveSettlement(UUID groupId, UUID settlementId, ResolveSettlementRequest request, String userEmail);

    /**
     * Retrieves all pending settlements for a group.
     *
     * @param groupId the group context
     */
    java.util.List<RecordSettlementResponse> getPendingSettlements(UUID groupId);
}
