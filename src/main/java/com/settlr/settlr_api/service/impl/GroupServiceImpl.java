package com.settlr.settlr_api.service.impl;

import com.settlr.settlr_api.dto.group.AddMemberRequest;
import com.settlr.settlr_api.dto.group.CreateGroupRequest;
import com.settlr.settlr_api.dto.group.GroupResponse;
import com.settlr.settlr_api.dto.group.MemberResponse;
import com.settlr.settlr_api.entity.Group;
import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.exception.ResourceNotFoundException;
import com.settlr.settlr_api.exception.UserAlreadyMemberException;
import com.settlr.settlr_api.repository.GroupRepository;
import com.settlr.settlr_api.repository.UserRepository;
import com.settlr.settlr_api.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final com.settlr.settlr_api.repository.UserBalanceRepository userBalanceRepository;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User loadUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private Group loadGroupById(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
    }

    private GroupResponse toResponse(Group group) {
        List<MemberResponse> members = group.getMembers().stream()
                .map(u -> new MemberResponse(u.getId(), u.getName(), u.getEmail()))
                .toList();

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getCreatedBy().getId(),
                members,
                group.getCreatedDate()
        );
    }

    // ── Operations ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String creatorEmail) {
        log.info("[GROUP] Creating group | name='{}' | creator={}", request.name(), creatorEmail);

        User creator = loadUserByEmail(creatorEmail);

        Group group = Group.builder()
                .name(request.name())
                .createdBy(creator)
                .build();

        group.getMembers().add(creator);   // creator is auto-added as first member

        Group saved = groupRepository.save(group);
        log.info("[GROUP] Created | groupId={} | creator={}", saved.getId(), creatorEmail);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public GroupResponse addMember(UUID groupId, AddMemberRequest request, String requesterEmail) {
        log.info("[GROUP] Adding member | groupId={} | newMember={} | requester={}",
                groupId, request.email(), requesterEmail);

        Group group = loadGroupById(groupId);

        // Authorization: only existing members can add people — 403 if not
        User requester = loadUserByEmail(requesterEmail);
        if (!groupRepository.isUserMemberOfGroup(groupId, requester.getId())) {
            log.warn("[GROUP] Unauthorized add-member attempt | groupId={} | requester={}",
                    groupId, requesterEmail);
            throw new AccessDeniedException("You are not a member of this group");
        }

        // Check for duplicate using the efficient DB query
        User newMember = loadUserByEmail(request.email());
        if (groupRepository.isUserMemberOfGroup(groupId, newMember.getId())) {
            throw new UserAlreadyMemberException(request.email(), group.getName());
        }

        group.getMembers().add(newMember);

        Group saved = groupRepository.save(group);
        log.info("[GROUP] Member added | groupId={} | newMemberId={}", saved.getId(), newMember.getId());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(String userEmail) {
        log.info("[GROUP] Fetching groups | user={}", userEmail);

        User user = loadUserByEmail(userEmail);
        List<Group> groups = groupRepository.findAllByMembersId(user.getId());

        log.info("[GROUP] Found {} group(s) | user={}", groups.size(), userEmail);
        return groups.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void deleteGroup(UUID groupId, String requesterEmail) {
        log.info("[GROUP] Deleting group | groupId={} | requester={}", groupId, requesterEmail);
        Group group = loadGroupById(groupId);
        User requester = loadUserByEmail(requesterEmail);
        
        // Only a group member can delete the group
        if (!groupRepository.isUserMemberOfGroup(groupId, requester.getId())) {
            throw new AccessDeniedException("Only group members can delete the group");
        }

        // Check for unsettled balances
        boolean hasUnsettledBalances = userBalanceRepository.findAllByGroupId(groupId).stream()
                .anyMatch(ub -> ub.getBalance().compareTo(java.math.BigDecimal.ZERO) != 0);

        if (hasUnsettledBalances) {
            throw new IllegalStateException("Cannot delete group: There are unsettled debts remaining.");
        }
        
        // Delete all UserBalance records (even zero balances) before deleting the group 
        // to prevent Hibernate TransientObjectException / ConstraintViolationException.
        userBalanceRepository.deleteAll(userBalanceRepository.findAllByGroupId(groupId));
        
        groupRepository.delete(group);
        log.info("[GROUP] Group deleted | groupId={}", groupId);
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID userId, String requesterEmail) {
        log.info("[GROUP] Removing member | groupId={} | userId={} | requester={}", groupId, userId, requesterEmail);
        Group group = loadGroupById(groupId);
        User requester = loadUserByEmail(requesterEmail);

        if (!groupRepository.isUserMemberOfGroup(groupId, requester.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }

        boolean removed = group.getMembers().removeIf(u -> u.getId().equals(userId));

        if (!removed) {
            throw new ResourceNotFoundException("Member", "id", userId);
        }
        groupRepository.save(group);
        log.info("[GROUP] Member removed | groupId={} | userId={}", groupId, userId);
    }
}
