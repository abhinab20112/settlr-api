package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.dto.balance.GroupSettlementPlanResponse;
import com.settlr.settlr_api.dto.balance.RecordSettlementRequest;
import com.settlr.settlr_api.dto.balance.RecordSettlementResponse;
import com.settlr.settlr_api.service.BalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/settle")
@RequiredArgsConstructor
public class SettlementController {

    private final BalanceService balanceService;

    /**
     * GET /api/groups/{groupId}/settle
     *
     * Calculates the minimum list of payments needed to clear all debts.
     * This is READ-ONLY — it does not record any payments.
     * Uses the min-cash-flow (greedy) algorithm.
     */
    @GetMapping
    public ResponseEntity<GroupSettlementPlanResponse> getSettlementPlan(
            @PathVariable UUID groupId,
            Authentication authentication) {

        String userEmail = authentication.getName();
        return ResponseEntity.ok(balanceService.getSettlementPlan(groupId, userEmail));
    }

    /**
     * POST /api/groups/{groupId}/settle
     *
     * Records an actual payment from the authenticated user to another user.
     * Both the Settlement record and the UserBalance update are in a single
     * @Transactional — if the balance update fails, the settlement record
     * is also rolled back (all-or-nothing).
     */
    @PostMapping
    public ResponseEntity<RecordSettlementResponse> recordSettlement(
            @PathVariable UUID groupId,
            @Valid @RequestBody RecordSettlementRequest request,
            Authentication authentication) {

        String payerEmail = authentication.getName();
        return ResponseEntity
                .ok(balanceService.recordSettlement(groupId, request, payerEmail));
    }
}
