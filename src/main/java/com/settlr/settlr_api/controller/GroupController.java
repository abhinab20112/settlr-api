package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.dto.group.AddMemberRequest;
import com.settlr.settlr_api.dto.group.CreateGroupRequest;
import com.settlr.settlr_api.dto.group.GroupResponse;
import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.exception.GroqException;
import com.settlr.settlr_api.exception.ResourceNotFoundException;
import com.settlr.settlr_api.repository.UserRepository;
import com.settlr.settlr_api.service.GroupService;
import com.settlr.settlr_api.service.ai.TripSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final TripSummaryService tripSummaryService;
    private final UserRepository userRepository;

    /**
     * POST /api/groups
     * Creates a new group. The authenticated user is auto-added as creator + first member.
     * UserId comes from the JWT via SecurityContext — not from the request body.
     */
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Authentication authentication) {

        String creatorEmail = authentication.getName();  // email from JWT principal
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(groupService.createGroup(request, creatorEmail));
    }

    /**
     * POST /api/groups/{groupId}/members
     * Adds a user (by email) to an existing group.
     * Only existing group members may add new members.
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupResponse> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request,
            Authentication authentication) {

        String requesterEmail = authentication.getName();
        return ResponseEntity.ok(groupService.addMember(groupId, request, requesterEmail));
    }

    /**
     * GET /api/groups
     * Returns all groups the authenticated user belongs to.
     */
    @GetMapping
    public ResponseEntity<List<GroupResponse>> getMyGroups(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(groupService.getMyGroups(userEmail));
    }

    /**
     * GET /api/groups/{groupId}/summary
     * Returns an AI-generated trip summary for the group.
     * On AI failure, returns 503 with fallback:true.
     */
    @GetMapping("/{groupId}/summary")
    public ResponseEntity<?> getGroupSummary(
            @PathVariable UUID groupId,
            Authentication authentication) {

        User user = resolveUser(authentication);

        try {
            TripSummaryService.TripSummary summary =
                    tripSummaryService.generateSummary(groupId, user.getId());
            return ResponseEntity.ok(summary);
        } catch (GroqException e) {
            log.warn("[SUMMARY] AI summary generation failed for group={}: {}", groupId, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "AI trip summary temporarily unavailable",
                            "fallback", true
                    ));
        }
    }

    /**
     * DELETE /api/groups/{groupId}
     * Deletes a group entirely. Only the creator can do this.
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID groupId,
            Authentication authentication) {
        String requesterEmail = authentication.getName();
        groupService.deleteGroup(groupId, requesterEmail);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/groups/{groupId}/members/{userId}
     * Removes a user from an existing group.
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            Authentication authentication) {
        String requesterEmail = authentication.getName();
        groupService.removeMember(groupId, userId, requesterEmail);
        return ResponseEntity.noContent().build();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private User resolveUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
