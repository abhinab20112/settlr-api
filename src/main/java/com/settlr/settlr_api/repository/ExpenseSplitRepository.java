package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {

    /**
     * All splits belonging to a specific expense.
     * Useful when recalculating or displaying a breakdown.
     */
    List<ExpenseSplit> findAllByExpenseId(UUID expenseId);

    /**
     * All splits assigned to a specific user (across all expenses).
     * Useful for showing a user's total obligations.
     */
    List<ExpenseSplit> findAllByUserId(UUID userId);
}
