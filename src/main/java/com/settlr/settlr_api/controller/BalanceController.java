package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.dto.balance.GroupBalanceSummaryResponse;
import com.settlr.settlr_api.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/balances")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    /**
     * GET /api/groups/{groupId}/balances
     *
     * Returns the net balance of every member in the group.
     * Positive → owed money. Negative → owes money.
     */
    @GetMapping
    public ResponseEntity<GroupBalanceSummaryResponse> getGroupBalances(
            @PathVariable UUID groupId,
            Authentication authentication) {

        String userEmail = authentication.getName();
        return ResponseEntity.ok(balanceService.getGroupBalances(groupId, userEmail));
    }
}
