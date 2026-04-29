package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.UserBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBalanceRepository extends JpaRepository<UserBalance, UUID> {

    /**
     * Find the exact balance record between two users in a group.
     * Used when updating a balance after an expense is added/settled.
     */
    Optional<UserBalance> findByFromUserIdAndToUserIdAndGroupId(
            UUID fromUserId, UUID toUserId, UUID groupId);

    /**
     * All balances where the user is the debtor (fromUser) in a group.
     */
    List<UserBalance> findAllByFromUserIdAndGroupId(UUID fromUserId, UUID groupId);

    /**
     * All balances where the user is the creditor (toUser) in a group.
     */
    List<UserBalance> findAllByToUserIdAndGroupId(UUID toUserId, UUID groupId);

    /**
     * All balances involving a user in a group — either as debtor or creditor.
     * Used to render the full "who owes whom" summary for a user in a group.
     */
    @Query("""
            SELECT ub FROM UserBalance ub
            WHERE ub.group.id = :groupId
              AND (ub.fromUser.id = :userId OR ub.toUser.id = :userId)
            """)
    List<UserBalance> findAllByUserIdAndGroupId(
            @Param("userId") UUID userId,
            @Param("groupId") UUID groupId);

    /**
     * All balances in a group — used to render the full group settlement summary.
     */
    List<UserBalance> findAllByGroupId(UUID groupId);
}
