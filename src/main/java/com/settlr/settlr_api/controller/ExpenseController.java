package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.dto.expense.CreateExpenseRequest;
import com.settlr.settlr_api.dto.expense.ExpenseResponse;
import com.settlr.settlr_api.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * POST /api/groups/{groupId}/expenses
     *
     * Creates an expense in the group. The payer is the authenticated user.
     * If participantEmails is empty/null, the expense is split equally
     * among all group members.
     *
     * Returns 201 Created with the full expense + splits breakdown.
     */
    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateExpenseRequest request,
            Authentication authentication) {

        String payerEmail = authentication.getName();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(expenseService.createExpense(groupId, request, payerEmail));
    }

    /**
     * GET /api/groups/{groupId}/expenses
     *
     * Lists all expenses in the group (newest first).
     * Each expense includes who paid, the total amount, and each person's share.
     * Only group members may access this.
     */
    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getGroupExpenses(
            @PathVariable UUID groupId,
            Authentication authentication) {

        String userEmail = authentication.getName();
        return ResponseEntity.ok(expenseService.getGroupExpenses(groupId, userEmail));
    }
}
