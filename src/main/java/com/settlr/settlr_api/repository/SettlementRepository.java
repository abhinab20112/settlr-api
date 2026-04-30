
package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    /** All settlements in a group, newest first. */
    List<Settlement> findAllByGroupIdOrderByCreatedDateDesc(UUID groupId);

    /** All settlements where a user paid someone in a group. */
    List<Settlement> findAllByGroupIdAndFromUserId(UUID groupId, UUID fromUserId);

    /** Find all settlements with a specific status in a group. */
    List<Settlement> findAllByGroupIdAndStatus(UUID groupId, com.settlr.settlr_api.entity.SettlementStatus status);
}
