package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /**
     * All expenses in a group created after a given date.
     * Eagerly fetches paidBy to avoid LazyInitializationException in async contexts.
     */
    @EntityGraph(attributePaths = {"paidBy", "group"})
    @Query("""
            SELECT e FROM Expense e
            WHERE e.group.id = :groupId
              AND e.createdDate >= :since
            ORDER BY e.createdDate DESC
            """)
    List<Expense> findAllByGroupIdAndCreatedDateAfter(
            @Param("groupId") UUID groupId,
            @Param("since") Instant since);

    /**
     * All expenses paid by a user across all groups, created after a given date.
     */
    @EntityGraph(attributePaths = {"paidBy", "group"})
    List<Expense> findAllByPaidByIdAndCreatedDateGreaterThanEqual(UUID paidById, Instant createdDate);
}
