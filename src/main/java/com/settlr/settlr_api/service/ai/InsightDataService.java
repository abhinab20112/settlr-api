package com.settlr.settlr_api.service.ai;

import com.settlr.settlr_api.entity.*;
import com.settlr.settlr_api.exception.ResourceNotFoundException;
import com.settlr.settlr_api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class InsightDataService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final SettlementRepository settlementRepository;

    // ── Records ──────────────────────────────────────────────────────────────

    public record GroupSummary(
            String groupName,
            BigDecimal totalSpend,
            int expenseCount,
            String topPayer
    ) {}

    public record UserExpenseContext(
            String userName,
            BigDecimal totalSpend,
            int expenseCount,
            String topCategory,
            Map<String, BigDecimal> categoryBreakdown,
            List<GroupSummary> groupSummaries,
            List<String> pendingSettlements,
            String dateRange
    ) {}

    /**
     * Complete context for a single group — designed to feed an AI prompt
     * that generates group-level trip insights.
     */
    public record GroupTripContext(
            String groupName,
            List<String> memberNames,
            BigDecimal totalSpend,
            int expenseCount,
            int daySpan,
            Map<String, BigDecimal> categoryBreakdown,
            String topSpender,
            String largestExpense,
            String settlementStatus,
            List<String> pendingPayments,
            String requestingUserSummary
    ) {}

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Builds a rich context object for a single group, suitable for AI-driven insights.
     *
     * @param groupId          the group to summarise
     * @param requestingUserId the user requesting the insight (must be a member)
     * @return a fully populated {@link GroupTripContext}
     * @throws AccessDeniedException     if the user is not a member of the group
     * @throws ResourceNotFoundException if the group or user does not exist
     */
    @Transactional(readOnly = true)
    public GroupTripContext buildGroupContext(UUID groupId, UUID requestingUserId) {
        log.info("[INSIGHTS] Building group context | groupId={} | userId={}", groupId, requestingUserId);

        // ── 1. Validate group + membership ───────────────────────────────────
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));


        if (!groupRepository.isUserMemberOfGroup(groupId, requestingUserId)) {
            throw new AccessDeniedException("You are not a member of this group");
        }

        // ── 2. Member names ──────────────────────────────────────────────────
        List<String> memberNames = group.getMembers().stream()
                .map(User::getName)
                .toList();

        // ── 3. All expenses in the group (all time) ──────────────────────────
        List<Expense> expenses = expenseRepository.findAllByGroupIdOrderByCreatedDateDesc(groupId);

        int expenseCount = expenses.size();

        BigDecimal totalSpend = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── 4. Day span (days between first and last expense) ────────────────
        int daySpan = 0;
        if (expenses.size() >= 2) {
            Instant earliest = expenses.get(expenses.size() - 1).getCreatedDate();
            Instant latest = expenses.get(0).getCreatedDate();
            daySpan = (int) ChronoUnit.DAYS.between(earliest, latest);
            if (daySpan == 0 && !earliest.equals(latest)) {
                daySpan = 1; // same-day but different timestamps
            }
        }

        // ── 5. Category breakdown (all expenses, not just user's) ────────────
        Map<Category, BigDecimal> categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .forEach(e -> categoryBreakdown.put(
                        e.getKey().getEmoji() + " " + e.getKey().getDisplayName(),
                        e.getValue()));

        // ── 6. Top spender (member who paid the most) ────────────────────────
        String topSpender = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getPaidBy().getName(),
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (₹" + e.getValue().stripTrailingZeros().toPlainString() + ")")
                .orElse("None");

        // ── 7. Largest single expense ────────────────────────────────────────
        String largestExpense = expenses.stream()
                .max(Comparator.comparing(Expense::getAmount))
                .map(e -> e.getDescription() + " — ₹" + e.getAmount().stripTrailingZeros().toPlainString())
                .orElse("None");

        // ── 8. Settlement status + pending payments ──────────────────────────
        List<UserBalance> balances = userBalanceRepository.findAllByGroupId(groupId);
        List<Settlement> pendingSettlementEntities = settlementRepository.findAllByGroupIdAndStatus(
                groupId, SettlementStatus.PENDING);

        List<String> pendingPayments = new ArrayList<>();

        // Unsettled balance-based debts
        for (UserBalance ub : balances) {
            BigDecimal amount = ub.getBalance();
            if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                pendingPayments.add(String.format("%s owes %s ₹%s",
                        ub.getFromUser().getName(),
                        ub.getToUser().getName(),
                        amount.stripTrailingZeros().toPlainString()));
            } else {
                pendingPayments.add(String.format("%s owes %s ₹%s",
                        ub.getToUser().getName(),
                        ub.getFromUser().getName(),
                        amount.negate().stripTrailingZeros().toPlainString()));
            }
        }

        // Include any in-flight PENDING settlement requests
        for (Settlement s : pendingSettlementEntities) {
            pendingPayments.add(String.format("%s has a pending payment of ₹%s to %s (awaiting confirmation)",
                    s.getFromUser().getName(),
                    s.getAmount().stripTrailingZeros().toPlainString(),
                    s.getToUser().getName()));
        }

        boolean allSettled = balances.stream()
                .allMatch(ub -> ub.getBalance().compareTo(BigDecimal.ZERO) == 0)
                && pendingSettlementEntities.isEmpty();

        String settlementStatus;
        if (allSettled) {
            settlementStatus = "Fully settled";
        } else {
            long membersWithDebt = balances.stream()
                    .filter(ub -> ub.getBalance().compareTo(BigDecimal.ZERO) != 0)
                    .flatMap(ub -> java.util.stream.Stream.of(ub.getFromUser().getId(), ub.getToUser().getId()))
                    .distinct()
                    .count();
            settlementStatus = membersWithDebt + " members have pending payments";
        }

        // ── 9. Requesting user summary ───────────────────────────────────────
        BigDecimal userPaid = expenses.stream()
                .filter(e -> e.getPaidBy().getId().equals(requestingUserId))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal userIsOwed = BigDecimal.ZERO;
        BigDecimal userOwes = BigDecimal.ZERO;
        for (UserBalance ub : balances) {
            BigDecimal amount = ub.getBalance();
            if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

            if (ub.getToUser().getId().equals(requestingUserId) && amount.compareTo(BigDecimal.ZERO) > 0) {
                userIsOwed = userIsOwed.add(amount);
            } else if (ub.getFromUser().getId().equals(requestingUserId) && amount.compareTo(BigDecimal.ZERO) > 0) {
                userOwes = userOwes.add(amount);
            } else if (ub.getToUser().getId().equals(requestingUserId) && amount.compareTo(BigDecimal.ZERO) < 0) {
                userOwes = userOwes.add(amount.negate());
            } else if (ub.getFromUser().getId().equals(requestingUserId) && amount.compareTo(BigDecimal.ZERO) < 0) {
                userIsOwed = userIsOwed.add(amount.negate());
            }
        }

        String requestingUserSummary;
        if (userPaid.compareTo(BigDecimal.ZERO) == 0 && userIsOwed.compareTo(BigDecimal.ZERO) == 0 && userOwes.compareTo(BigDecimal.ZERO) == 0) {
            requestingUserSummary = "You have no expenses or debts in this group";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("You paid ₹").append(userPaid.stripTrailingZeros().toPlainString());
            if (userIsOwed.compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" and are owed ₹").append(userIsOwed.stripTrailingZeros().toPlainString());
            }
            if (userOwes.compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" and owe ₹").append(userOwes.stripTrailingZeros().toPlainString());
            }
            requestingUserSummary = sb.toString();
        }

        log.info("[INSIGHTS] Group context built | group={} | expenses={} | members={} | pending={}",
                group.getName(), expenseCount, memberNames.size(), pendingPayments.size());

        return new GroupTripContext(
                group.getName(),
                memberNames,
                totalSpend,
                expenseCount,
                daySpan,
                categoryBreakdown,
                topSpender,
                largestExpense,
                settlementStatus,
                pendingPayments,
                requestingUserSummary
        );
    }

    @Transactional(readOnly = true)
    public UserExpenseContext buildContext(UUID userId, int days) {
        log.info("[INSIGHTS] Building context | userId={} | days={}", userId, days);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        String dateRange = "Last " + days + " days";

        // ── 1. Gather all groups the user belongs to ─────────────────────────
        List<Group> groups = groupRepository.findAllByMembersId(userId);

        // ── 2. For each group, fetch expenses within the date window ─────────
        List<Expense> allExpenses = new ArrayList<>();
        Map<UUID, List<Expense>> expensesByGroup = new LinkedHashMap<>();

        for (Group group : groups) {
            List<Expense> groupExpenses =
                    expenseRepository.findAllByGroupIdAndCreatedDateAfter(group.getId(), since);
            expensesByGroup.put(group.getId(), groupExpenses);
            allExpenses.addAll(groupExpenses);
        }

        // ── 3. Total spend (expenses the user PAID for) ──────────────────────
        BigDecimal totalSpend = allExpenses.stream()
                .filter(e -> e.getPaidBy().getId().equals(userId))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int expenseCount = (int) allExpenses.stream()
                .filter(e -> e.getPaidBy().getId().equals(userId))
                .count();

        // ── 4. Category breakdown (all group expenses the user paid) ─────────
        Map<Category, BigDecimal> categoryTotals = allExpenses.stream()
                .filter(e -> e.getPaidBy().getId().equals(userId))
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        // Convert enum keys to displayName
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .forEach(e -> categoryBreakdown.put(
                        e.getKey().getEmoji() + " " + e.getKey().getDisplayName(),
                        e.getValue()));

        // Top category
        String topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getDisplayName())
                .orElse("None");

        // ── 5. Group summaries ───────────────────────────────────────────────
        List<GroupSummary> groupSummaries = new ArrayList<>();
        for (Group group : groups) {
            List<Expense> groupExpenses = expensesByGroup.getOrDefault(group.getId(), List.of());
            if (groupExpenses.isEmpty()) continue;

            BigDecimal groupTotal = groupExpenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Find the person who paid the most in this group
            String topPayer = groupExpenses.stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getPaidBy().getName(),
                            Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                    ))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Unknown");

            groupSummaries.add(new GroupSummary(
                    group.getName(),
                    groupTotal,
                    groupExpenses.size(),
                    topPayer
            ));
        }

        // ── 6. Pending settlements (human-readable) ──────────────────────────
        List<String> pendingSettlements = new ArrayList<>();
        for (Group group : groups) {
            List<UserBalance> balances = userBalanceRepository.findAllByGroupId(group.getId());
            for (UserBalance ub : balances) {
                BigDecimal amount = ub.getBalance();
                if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

                if (ub.getFromUser().getId().equals(userId) && amount.compareTo(BigDecimal.ZERO) > 0) {
                    pendingSettlements.add(String.format(
                            "You owe %s ₹%s in %s",
                            ub.getToUser().getName(),
                            amount.stripTrailingZeros().toPlainString(),
                            group.getName()));
                } else if (ub.getToUser().getId().equals(userId) && amount.compareTo(BigDecimal.ZERO) > 0) {
                    pendingSettlements.add(String.format(
                            "%s owes you ₹%s in %s",
                            ub.getFromUser().getName(),
                            amount.stripTrailingZeros().toPlainString(),
                            group.getName()));
                }
            }
        }

        log.info("[INSIGHTS] Context built | user={} | expenses={} | groups={} | settlements={}",
                user.getName(), expenseCount, groupSummaries.size(), pendingSettlements.size());

        return new UserExpenseContext(
                user.getName(),
                totalSpend,
                expenseCount,
                topCategory,
                categoryBreakdown,
                groupSummaries,
                pendingSettlements,
                dateRange
        );
    }
}
