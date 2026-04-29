package com.settlr.settlr_api.service;

import com.settlr.settlr_api.dto.group.AddMemberRequest;
import com.settlr.settlr_api.dto.group.CreateGroupRequest;
import com.settlr.settlr_api.dto.group.GroupResponse;

import java.util.List;
import java.util.UUID;

public interface GroupService {

    /**
     * Creates a group and automatically adds the creator as the first member.
     *
     * @param request      group creation payload
     * @param creatorEmail email of the authenticated user creating the group
     */
    GroupResponse createGroup(CreateGroupRequest request, String creatorEmail);

    /**
     * Adds a user (looked up by email) to an existing group.
     * Only existing group members may add new members.
     *
     * @param groupId        target group
     * @param request        payload containing the new member's email
     * @param requesterEmail email of the authenticated user making the request
     */
    GroupResponse addMember(UUID groupId, AddMemberRequest request, String requesterEmail);

    /**
     * Returns all groups the authenticated user belongs to.
     *
     * @param userEmail email of the authenticated user
     */
    List<GroupResponse> getMyGroups(String userEmail);

    /**
     * Deletes a group entirely.
     */
    void deleteGroup(UUID groupId, String requesterEmail);

    /**
     * Removes a member from a group.
     */
    void removeMember(UUID groupId, UUID userId, String requesterEmail);
}
