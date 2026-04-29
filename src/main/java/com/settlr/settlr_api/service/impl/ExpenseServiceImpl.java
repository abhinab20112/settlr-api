package com.settlr.settlr_api.service.impl;

import com.settlr.settlr_api.dto.expense.CreateExpenseRequest;
import com.settlr.settlr_api.dto.expense.ExpenseResponse;
import com.settlr.settlr_api.dto.expense.SplitResponse;
import com.settlr.settlr_api.entity.*;
import com.settlr.settlr_api.exception.BalanceConflictException;
import com.settlr.settlr_api.exception.ResourceNotFoundException;
import com.settlr.settlr_api.repository.*;
import com.settlr.settlr_api.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserBalanceRepository userBalanceRepository;

    @Override
    @Transactional
    public ExpenseResponse createExpense(UUID groupId, CreateExpenseRequest request, String payerEmail) {
        log.info("[EXPENSE] Creating expense | group={} | payer={} | amount={} {}",
                groupId, payerEmail, request.amount(), request.currency());

        // ── 1. Load & validate ───────────────────────────────────────────────
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        User payer = userRepository.findByEmail(payerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", payerEmail));

        // Payer must be a member of the group — 403 if not (not 404)
        if (!groupRepository.isUserMemberOfGroup(groupId, payer.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }

        // ── 2. Resolve participants ──────────────────────────────────────────
        List<User> participants = resolveParticipants(request, group);

        // Payer must be among participants (they share the expense too)
        if (participants.stream().noneMatch(u -> u.getId().equals(payer.getId()))) {
            participants.add(payer);
        }

        log.info("[EXPENSE] Splitting among {} participants", participants.size());

        // ── 3. Calculate equal split (BigDecimal — NEVER double) ─────────────
        int count = participants.size();
        BigDecimal perPerson = request.amount()
                .divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);

        // Handle rounding: first participant absorbs the remainder
        BigDecimal remainder = request.amount()
                .subtract(perPerson.multiply(BigDecimal.valueOf(count)));

        // ── 4. Create Expense entity ─────────────────────────────────────────
        Expense expense = Expense.builder()
                .group(group)
                .paidBy(payer)
                .description(request.description())
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .build();

        // ── 5. Create ExpenseSplit records ────────────────────────────────────
        List<ExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            User participant = participants.get(i);
            BigDecimal share = (i == 0) ? perPerson.add(remainder) : perPerson;

            ExpenseSplit split = ExpenseSplit.builder()
                    .expense(expense)
                    .user(participant)
                    .share(share)
                    .build();
            splits.add(split);
        }
        expense.setSplits(splits);

        Expense saved = expenseRepository.save(expense);   // cascades to splits
        log.info("[EXPENSE] Expense created | expenseId={}", saved.getId());

        // ── 6. Update UserBalance records ────────────────────────────────────
        try {
            updateBalances(payer, participants, splits, group);
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.error("[EXPENSE] Optimistic lock conflict on UserBalance | expenseId={}", saved.getId(), ex);
            throw new BalanceConflictException(
                    "Concurrent balance update detected — please retry", ex);
        }

        // ── 7. Build response ────────────────────────────────────────────────
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getGroupExpenses(UUID groupId, String userEmail) {
        log.info("[EXPENSE] Listing expenses | groupId={} | user={}", groupId, userEmail);

        // Validate group exists
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", "id", groupId);
        }

        // Validate user is a member
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        if (!groupRepository.isUserMemberOfGroup(groupId, user.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }

        List<Expense> expenses = expenseRepository.findAllByGroupIdOrderByCreatedDateDesc(groupId);
        log.info("[EXPENSE] Found {} expense(s) | groupId={}", expenses.size(), groupId);

        return expenses.stream().map(this::toResponse).toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves participants: if emails are provided, load those users;
     * otherwise, split among ALL group members.
     */
    private List<User> resolveParticipants(CreateExpenseRequest request, Group group) {
        if (request.participantEmails() == null || request.participantEmails().isEmpty()) {
            return new ArrayList<>(group.getMembers());
        }

        List<User> participants = new ArrayList<>();
        for (String email : request.participantEmails()) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

            if (!groupRepository.isUserMemberOfGroup(group.getId(), user.getId())) {
                throw new IllegalArgumentException(
                        "User '" + email + "' is not a member of group '" + group.getName() + "'");
            }
            participants.add(user);
        }
        return participants;
    }

    /**
     * For each non-payer participant, update the UserBalance between
     * participant (debtor) → payer (creditor) in this group.
     *
     * Uses find-or-create and relies on @Version for optimistic locking.
     */
    private void updateBalances(User payer, List<User> participants,
                                List<ExpenseSplit> splits, Group group) {
        for (ExpenseSplit split : splits) {
            User debtor = split.getUser();

            // Payer doesn't owe themselves
            if (debtor.getId().equals(payer.getId())) {
                continue;
            }

            BigDecimal shareAmount = split.getShare();

            // Check if there's already a balance record in either direction
            // Direction 1: debtor → payer (natural direction)
            var existingBalance = userBalanceRepository
                    .findByFromUserIdAndToUserIdAndGroupId(debtor.getId(), payer.getId(), group.getId());

            if (existingBalance.isPresent()) {
                UserBalance balance = existingBalance.get();
                balance.setBalance(balance.getBalance().add(shareAmount));
                userBalanceRepository.save(balance);
                log.debug("[BALANCE] Updated | {} → {} | +{} | new={}",
                        debtor.getEmail(), payer.getEmail(), shareAmount, balance.getBalance());
                continue;
            }

            // Direction 2: payer → debtor (reverse — debtor's payment reduces existing debt)
            var reverseBalance = userBalanceRepository
                    .findByFromUserIdAndToUserIdAndGroupId(payer.getId(), debtor.getId(), group.getId());

            if (reverseBalance.isPresent()) {
                UserBalance balance = reverseBalance.get();
                balance.setBalance(balance.getBalance().subtract(shareAmount));
                userBalanceRepository.save(balance);
                log.debug("[BALANCE] Updated reverse | {} → {} | -{} | new={}",
                        payer.getEmail(), debtor.getEmail(), shareAmount, balance.getBalance());
                continue;
            }

            // No existing record — create new: debtor owes payer
            UserBalance newBalance = UserBalance.builder()
                    .fromUser(debtor)
                    .toUser(payer)
                    .group(group)
                    .balance(shareAmount)
                    .build();
            userBalanceRepository.save(newBalance);
            log.debug("[BALANCE] Created | {} → {} | {}",
                    debtor.getEmail(), payer.getEmail(), shareAmount);
        }
    }

    private ExpenseResponse toResponse(Expense expense) {
        List<SplitResponse> splitResponses = expense.getSplits().stream()
                .map(s -> new SplitResponse(
                        s.getUser().getId(),
                        s.getUser().getName(),
                        s.getUser().getEmail(),
                        s.getShare()))
                .toList();

        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getGroup().getName(),
                expense.getPaidBy().getId(),
                expense.getPaidBy().getName(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCurrency(),
                splitResponses,
                expense.getCreatedDate()
        );
    }
}
