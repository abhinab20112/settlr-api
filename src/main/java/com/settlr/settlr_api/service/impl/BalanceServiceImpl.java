package com.settlr.settlr_api.service.impl;

import com.settlr.settlr_api.dto.balance.*;
import com.settlr.settlr_api.util.SettlementCalculator;
import com.settlr.settlr_api.util.SettlementTransaction;
import com.settlr.settlr_api.entity.Group;
import com.settlr.settlr_api.entity.Settlement;
import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.entity.UserBalance;
import com.settlr.settlr_api.exception.BalanceConflictException;
import com.settlr.settlr_api.exception.ResourceNotFoundException;
import com.settlr.settlr_api.repository.GroupRepository;
import com.settlr.settlr_api.repository.SettlementRepository;
import com.settlr.settlr_api.repository.UserBalanceRepository;
import com.settlr.settlr_api.repository.UserRepository;
import com.settlr.settlr_api.service.BalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@SuppressWarnings("null")
public class BalanceServiceImpl implements BalanceService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final SettlementRepository settlementRepository;
    private final Executor balanceExecutor;

    public BalanceServiceImpl(
            GroupRepository groupRepository,
            UserRepository userRepository,
            UserBalanceRepository userBalanceRepository,
            SettlementRepository settlementRepository,
            @Qualifier("balanceExecutor") Executor balanceExecutor) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.settlementRepository = settlementRepository;
        this.balanceExecutor = balanceExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public GroupBalanceSummaryResponse getGroupBalances(UUID groupId, String userEmail) {
        log.info("[BALANCE] Fetching group balances | groupId={} | user={}", groupId, userEmail);

        // ── 1. Validate group exists ─────────────────────────────────────────
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        // ── 2. Validate requester is a member ────────────────────────────────
        User requester = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        if (!groupRepository.isUserMemberOfGroup(groupId, requester.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }

        // ── 3. Read ALL UserBalance rows in this group ───────────────────────
        List<UserBalance> balances = userBalanceRepository.findAllByGroupId(groupId);

        return buildGroupSummary(group, balances);
    }

    /*
     * ═══════════════════════════════════════════════════════════════════════════
     * WHY PARALLEL (CompletableFuture.supplyAsync) IS FASTER THAN SEQUENTIAL
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * A user might belong to N groups (e.g., 10). For each group, we need to:
     *   1. Query UserBalance rows from PostgreSQL
     *   2. Aggregate them into net balances
     *
     * SEQUENTIAL approach:
     *   Total time ≈ N × (DB query time + aggregation time)
     *   If each query takes 20ms → 10 groups = 200ms
     *
     * PARALLEL approach (CompletableFuture.supplyAsync with a thread pool):
     *   All N queries run concurrently on separate threads.
     *   Total time ≈ max(single query time) + aggregation overhead
     *   If each query takes 20ms → 10 groups ≈ 20-30ms (near-constant)
     *
     * This works because DB queries are I/O-bound — the thread is just WAITING
     * for the database to respond. While Thread A waits for Group 1's query,
     * Thread B can fire Group 2's query simultaneously. The database can handle
     * many concurrent reads with no contention (read-only, no locks).
     *
     * The dedicated "balanceExecutor" thread pool ensures we don't starve the
     * main request-handling threads (Tomcat's pool), and it's bounded to avoid
     * runaway thread creation under load.
     * ═══════════════════════════════════════════════════════════════════════════
     */
    @Override
    public UserBalanceSummaryResponse getUserBalancesAcrossGroups(String userEmail) {
        log.info("[BALANCE] Fetching cross-group balances | user={}", userEmail);

        // ── 1. Load user and their groups ────────────────────────────────────
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        List<Group> groups = groupRepository.findAllByMembersId(user.getId());
        log.info("[BALANCE] User belongs to {} group(s) | user={}", groups.size(), userEmail);

        if (groups.isEmpty()) {
            return new UserBalanceSummaryResponse(
                    user.getName(), user.getEmail(), BigDecimal.ZERO, List.of());
        }

        // Initialize lazy-loaded members on the main thread (which has an active Hibernate 
        // Session via OpenSessionInView) so the background threads don't crash.
        groups.forEach(g -> org.hibernate.Hibernate.initialize(g.getMembers()));

        // ── 2. Fetch each group's balances IN PARALLEL ───────────────────────
        List<CompletableFuture<GroupBalanceSummaryResponse>> futures = groups.stream()
                .map(group -> CompletableFuture.supplyAsync(
                        () -> {
                            List<UserBalance> balances =
                                    userBalanceRepository.findAllByGroupId(group.getId());
                            return buildGroupSummary(group, balances);
                        },
                        balanceExecutor  // dedicated thread pool — never starves Tomcat
                ))
                .toList();

        // ── 3. Join all results ──────────────────────────────────────────────
        List<GroupBalanceSummaryResponse> groupBalances = futures.stream()
                .map(CompletableFuture::join)   // blocks until each future completes
                .toList();

        // ── 4. Compute total net balance across all groups ───────────────────
        BigDecimal totalNet = groupBalances.stream()
                .flatMap(gb -> gb.memberBalances().stream())
                .filter(mb -> mb.userId().equals(user.getId()))
                .map(MemberBalanceResponse::netBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("[BALANCE] Cross-group total net={} | user={}", totalNet, userEmail);

        return new UserBalanceSummaryResponse(
                user.getName(), user.getEmail(), totalNet, groupBalances);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupSettlementPlanResponse getSettlementPlan(UUID groupId, String userEmail) {
        log.info("[SETTLE] Calculating settlement plan | groupId={} | user={}", groupId, userEmail);

        // ── 1. Validate group + membership ───────────────────────────────────
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        User requester = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        if (!groupRepository.isUserMemberOfGroup(groupId, requester.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }

        // ── 2. Build net balances (reuse existing aggregation logic) ─────────
        List<UserBalance> balanceRows = userBalanceRepository.findAllByGroupId(groupId);

        Map<UUID, BigDecimal> netMap = new LinkedHashMap<>();
        Map<UUID, User> userMap = new LinkedHashMap<>();

        for (User member : group.getMembers()) {
            netMap.put(member.getId(), BigDecimal.ZERO);
            userMap.put(member.getId(), member);
        }

        for (UserBalance ub : balanceRows) {
            UUID fromId = ub.getFromUser().getId();
            UUID toId = ub.getToUser().getId();
            BigDecimal amount = ub.getBalance();

            netMap.merge(fromId, amount.negate(), BigDecimal::add);
            netMap.merge(toId, amount, BigDecimal::add);

            userMap.putIfAbsent(fromId, ub.getFromUser());
            userMap.putIfAbsent(toId, ub.getToUser());
        }

        // ── 2.5 Adjust netMap for PENDING settlements ────────────────────────
        // If Alice pays Bob $1150 and it's PENDING, her debt is effectively reduced
        // in the suggested plan to avoid double-payment.
        List<Settlement> pendingSettlements = settlementRepository.findAllByGroupIdAndStatus(
                groupId, com.settlr.settlr_api.entity.SettlementStatus.PENDING);

        for (Settlement pending : pendingSettlements) {
            UUID fromId = pending.getFromUser().getId();
            UUID toId = pending.getToUser().getId();
            BigDecimal amount = pending.getAmount();

            // The payer's negative balance is increased (made closer to 0)
            netMap.merge(fromId, amount, BigDecimal::add);
            // The receiver's positive balance is decreased (made closer to 0)
            netMap.merge(toId, amount.negate(), BigDecimal::add);
        }

        // ── 3. Run the min-cash-flow algorithm ───────────────────────────────
        List<SettlementTransaction> rawTxns = SettlementCalculator.calculate(netMap);

        // ── 4. Enrich with user details ──────────────────────────────────────
        List<SettlementResponse> transactions = rawTxns.stream()
                .map(t -> {
                    User from = userMap.get(t.fromUserId());
                    User to = userMap.get(t.toUserId());
                    return new SettlementResponse(
                            from.getId(), from.getName(), from.getEmail(),
                            to.getId(), to.getName(), to.getEmail(),
                            t.amount());
                })
                .toList();

        log.info("[SETTLE] Plan ready | groupId={} | transactions={}", groupId, transactions.size());

        return new GroupSettlementPlanResponse(
                group.getId(), group.getName(), transactions.size(), transactions);
    }

    @Override
    @Transactional
    public RecordSettlementResponse recordSettlement(UUID groupId, RecordSettlementRequest request, String authenticatedEmail) {
        log.info("[SETTLE] Initiating payment | groupId={} | authenticatedUser={} | from={} | to={} | amount={}",
                groupId, authenticatedEmail, request.fromEmail(), request.toEmail(), request.amount());

        // ── 1. Determine roles ────────────────────────────────────────────────
        String actualFromEmail = (request.fromEmail() != null && !request.fromEmail().isBlank()) 
                ? request.fromEmail() : authenticatedEmail;
        String actualToEmail = request.toEmail();

        if (!authenticatedEmail.equals(actualFromEmail) && !authenticatedEmail.equals(actualToEmail)) {
            throw new AccessDeniedException("You can only record payments you are involved in");
        }

        // ── 2. Validate group, payer, and recipient ──────────────────────────
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        User payer = userRepository.findByEmail(actualFromEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", actualFromEmail));

        if (!groupRepository.isUserMemberOfGroup(groupId, payer.getId())) {
            throw new AccessDeniedException("User '" + actualFromEmail + "' is not a member of this group");
        }

        User recipient = userRepository.findByEmail(actualToEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", actualToEmail));

        if (!groupRepository.isUserMemberOfGroup(groupId, recipient.getId())) {
            throw new IllegalArgumentException("User '" + actualToEmail + "' is not a member of this group");
        }

        if (payer.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("Cannot settle with yourself");
        }

        // ── 3. Save the Settlement record ────────────────────────────────────
        // If receiver initiated it, auto-confirm it!
        boolean isReceiverInitiated = authenticatedEmail.equals(actualToEmail);
        com.settlr.settlr_api.entity.SettlementStatus initialStatus = isReceiverInitiated 
                ? com.settlr.settlr_api.entity.SettlementStatus.CONFIRMED 
                : com.settlr.settlr_api.entity.SettlementStatus.PENDING;

        Settlement settlement = Settlement.builder()
                .group(group)
                .fromUser(payer)
                .toUser(recipient)
                .amount(request.amount())
                .status(initialStatus)
                .build();

        if (isReceiverInitiated) {
            settlement.setResolvedDate(java.time.Instant.now());
            // Immediately update balances
            try {
                updateBalanceForSettlement(payer, recipient, group, request.amount());
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.error("[SETTLE] Optimistic lock conflict while auto-confirming | from={} | to={}", payer.getEmail(), recipient.getEmail(), ex);
                throw new BalanceConflictException("Concurrent balance update detected — please try clearing the debt again", ex);
            }
        }

        Settlement saved = settlementRepository.save(settlement);
        log.info("[SETTLE] Settlement record saved as PENDING | settlementId={}", saved.getId());

        return new RecordSettlementResponse(
                saved.getId(),
                group.getId(), group.getName(),
                payer.getId(), payer.getName(),
                recipient.getId(), recipient.getName(),
                saved.getAmount(),
                saved.getStatus(),
                saved.getCreatedDate(),
                saved.getResolvedDate()
        );
    }

    @Override
    @Transactional
    public RecordSettlementResponse resolveSettlement(UUID groupId, UUID settlementId, ResolveSettlementRequest request, String userEmail) {
        log.info("[SETTLE] Resolving payment | settlementId={} | confirm={} | user={}",
                settlementId, request.confirm(), userEmail);

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", "id", settlementId));

        if (!settlement.getGroup().getId().equals(groupId)) {
            throw new IllegalArgumentException("Settlement does not belong to this group");
        }

        if (settlement.getStatus() != com.settlr.settlr_api.entity.SettlementStatus.PENDING) {
            throw new IllegalArgumentException("Settlement is already " + settlement.getStatus());
        }

        User recipient = settlement.getToUser();
        if (!recipient.getEmail().equals(userEmail)) {
            throw new AccessDeniedException("Only the recipient can resolve this settlement");
        }

        if (request.confirm()) {
            // Update balances
            try {
                updateBalanceForSettlement(settlement.getFromUser(), recipient, settlement.getGroup(), settlement.getAmount());
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.error("[SETTLE] Optimistic lock conflict while confirming | settlementId={}", settlement.getId(), ex);
                throw new BalanceConflictException(
                        "Concurrent balance update detected — please retry confirming", ex);
            }
            settlement.setStatus(com.settlr.settlr_api.entity.SettlementStatus.CONFIRMED);
        } else {
            settlement.setStatus(com.settlr.settlr_api.entity.SettlementStatus.REJECTED);
        }

        settlement.setResolvedDate(java.time.Instant.now());
        Settlement saved = settlementRepository.save(settlement);

        return new RecordSettlementResponse(
                saved.getId(),
                saved.getGroup().getId(), saved.getGroup().getName(),
                saved.getFromUser().getId(), saved.getFromUser().getName(),
                saved.getToUser().getId(), saved.getToUser().getName(),
                saved.getAmount(),
                saved.getStatus(),
                saved.getCreatedDate(),
                saved.getResolvedDate()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<RecordSettlementResponse> getPendingSettlements(UUID groupId) {
        log.info("[SETTLE] Fetching pending settlements | groupId={}", groupId);

        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", "id", groupId);
        }

        return settlementRepository.findAllByGroupIdAndStatus(groupId, com.settlr.settlr_api.entity.SettlementStatus.PENDING)
                .stream()
                .map(s -> new RecordSettlementResponse(
                        s.getId(),
                        s.getGroup().getId(), s.getGroup().getName(),
                        s.getFromUser().getId(), s.getFromUser().getName(),
                        s.getToUser().getId(), s.getToUser().getName(),
                        s.getAmount(),
                        s.getStatus(),
                        s.getCreatedDate(),
                        s.getResolvedDate()
                ))
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Reduces the payer's debt to the recipient (or increases the recipient's
     * debt to payer if overpaying). Relies on @Version optimistic locking.
     */
    private void updateBalanceForSettlement(User payer, User recipient, Group group, BigDecimal amount) {
        // Direction 1: payer → recipient (payer owes recipient)
        var payerOwes = userBalanceRepository
                .findByFromUserIdAndToUserIdAndGroupId(payer.getId(), recipient.getId(), group.getId());

        if (payerOwes.isPresent()) {
            UserBalance balance = payerOwes.get();
            balance.setBalance(balance.getBalance().subtract(amount));
            userBalanceRepository.save(balance);
            log.debug("[SETTLE-BALANCE] Reduced | {} → {} | -{} | new={}",
                    payer.getEmail(), recipient.getEmail(), amount, balance.getBalance());
            return;
        }

        // Direction 2: recipient → payer (recipient owes payer — payment increases their debt)
        var recipientOwes = userBalanceRepository
                .findByFromUserIdAndToUserIdAndGroupId(recipient.getId(), payer.getId(), group.getId());

        if (recipientOwes.isPresent()) {
            UserBalance balance = recipientOwes.get();
            balance.setBalance(balance.getBalance().add(amount));
            userBalanceRepository.save(balance);
            log.debug("[SETTLE-BALANCE] Increased reverse | {} → {} | +{} | new={}",
                    recipient.getEmail(), payer.getEmail(), amount, balance.getBalance());
            return;
        }

        // No existing balance — create new: recipient now owes payer (overpayment)
        UserBalance newBalance = UserBalance.builder()
                .fromUser(recipient)
                .toUser(payer)
                .group(group)
                .balance(amount)
                .build();
        userBalanceRepository.save(newBalance);
        log.debug("[SETTLE-BALANCE] Created | {} → {} | {}",
                recipient.getEmail(), payer.getEmail(), amount);
    }

    /**
     * Aggregates UserBalance rows into a GroupBalanceSummaryResponse.
     * Shared by both single-group and cross-group methods.
     */
    private GroupBalanceSummaryResponse buildGroupSummary(Group group, List<UserBalance> balances) {
        Map<UUID, BigDecimal> netMap = new LinkedHashMap<>();
        Map<UUID, User> userMap = new LinkedHashMap<>();

        // Seed all group members at zero (so settled members still appear)
        for (User member : group.getMembers()) {
            netMap.put(member.getId(), BigDecimal.ZERO);
            userMap.put(member.getId(), member);
        }

        // Walk each balance row
        for (UserBalance ub : balances) {
            UUID fromId = ub.getFromUser().getId();
            UUID toId = ub.getToUser().getId();
            BigDecimal amount = ub.getBalance();

            // fromUser owes → their net decreases
            netMap.merge(fromId, amount.negate(), BigDecimal::add);
            // toUser is owed → their net increases
            netMap.merge(toId, amount, BigDecimal::add);

            // Ensure users are in the lookup map (in case they left the group)
            userMap.putIfAbsent(fromId, ub.getFromUser());
            userMap.putIfAbsent(toId, ub.getToUser());
        }

        List<MemberBalanceResponse> memberBalances = netMap.entrySet().stream()
                .map(entry -> {
                    User u = userMap.get(entry.getKey());
                    return new MemberBalanceResponse(
                            u.getId(), u.getName(), u.getEmail(), entry.getValue());
                })
                .toList();

        return new GroupBalanceSummaryResponse(group.getId(), group.getName(), memberBalances);
    }
}
