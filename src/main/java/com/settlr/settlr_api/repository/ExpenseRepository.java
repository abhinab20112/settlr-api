package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /**
     * All expenses in a group, newest first.
     * "createdDate" is inherited from BaseEntity — Spring Data resolves it correctly.
     */
    List<Expense> findAllByGroupIdOrderByCreatedDateDesc(UUID groupId);

    /**
     * All expenses paid by a specific user across all groups.
     */
    List<Expense> findAllByPaidById(UUID userId);

    /**
     * All expenses paid by a specific user within a specific group.
     */
    List<Expense> findAllByGroupIdAndPaidById(UUID groupId, UUID paidById);
}
