package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.dto.balance.UserBalanceSummaryResponse;
import com.settlr.settlr_api.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final BalanceService balanceService;

    /**
     * GET /api/users/me/balances
     *
     * Returns the authenticated user's net balance across ALL their groups.
     * Each group's balance is fetched in parallel using CompletableFuture.
     */
    @GetMapping("/balances")
    public ResponseEntity<UserBalanceSummaryResponse> getMyBalances(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(balanceService.getUserBalancesAcrossGroups(userEmail));
    }
}
