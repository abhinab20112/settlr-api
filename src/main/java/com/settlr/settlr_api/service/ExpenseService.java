package com.settlr.settlr_api.service;

import com.settlr.settlr_api.dto.expense.CreateExpenseRequest;
import com.settlr.settlr_api.dto.expense.ExpenseResponse;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {

    /**
     * Creates an expense in a group, splits it among participants,
     * and updates all affected UserBalance records.
     *
     * @param groupId    the group this expense belongs to
     * @param request    expense creation payload
     * @param payerEmail email of the authenticated user who paid
     * @throws com.settlr.settlr_api.exception.BalanceConflictException if optimistic lock fails
     */
    ExpenseResponse createExpense(UUID groupId, CreateExpenseRequest request, String payerEmail);

    /**
     * Lists all expenses in a group (newest first), with payer info and per-person splits.
     *
     * @param groupId   the group to list expenses for
     * @param userEmail email of the authenticated user (must be a group member)
     */
    List<ExpenseResponse> getGroupExpenses(UUID groupId, String userEmail);
}
