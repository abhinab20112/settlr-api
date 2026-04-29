package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    /**
     * Find all groups a user belongs to (as a member).
     * Spring Data traverses the @ManyToMany members collection via "membersId".
     */
    List<Group> findAllByMembersId(UUID userId);

    /**
     * Find all groups created by a specific user.
     */
    List<Group> findAllByCreatedById(UUID userId);

    /**
     * Check whether a user is already a member of a specific group.
     */
    @Query("SELECT COUNT(g) > 0 FROM Group g JOIN g.members m WHERE g.id = :groupId AND m.id = :userId")
    boolean isUserMemberOfGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
