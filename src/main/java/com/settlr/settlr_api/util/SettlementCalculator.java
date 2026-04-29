package com.settlr.settlr_api.util;

import java.math.BigDecimal;
import java.util.*;

/**
 * Min-Cash-Flow (Minimum Transactions) Debt Settlement Algorithm.
 *
 * <h3>Problem</h3>
 * Given N people with net balances (positive = owed money, negative = owes money),
 * find the minimum number of transactions to settle all debts.
 *
 * <h3>Algorithm (Greedy)</h3>
 * <ol>
 *   <li>Separate users into two lists: debtors (negative balance) and creditors (positive balance).</li>
 *   <li>Sort debtors by amount ascending (most in debt first), creditors by amount descending (most owed first).</li>
 *   <li>Match the largest debtor with the largest creditor:
 *       <ul>
 *           <li>Transfer the minimum of |debt| and credit.</li>
 *           <li>Reduce both balances by the transferred amount.</li>
 *           <li>If a debtor is fully settled, move to the next debtor.</li>
 *           <li>If a creditor is fully settled, move to the next creditor.</li>
 *       </ul>
 *   </li>
 *   <li>Repeat until all balances are zero.</li>
 * </ol>
 *
 * <h3>Why this works</h3>
 * The greedy approach produces at most (N-1) transactions for N participants,
 * which is optimal for the general case. It correctly handles circular debts
 * (A→B→C→A) by collapsing them into direct transfers.
 *
 * <h3>Complexity</h3>
 * Time: O(N log N) for sorting + O(N) for the two-pointer sweep = O(N log N).
 * Space: O(N) for the debtor/creditor lists.
 *
 * <p>This is a pure utility class with no Spring dependencies — fully unit-testable.</p>
 */
public final class SettlementCalculator {

    private SettlementCalculator() {
        // Utility class — no instantiation
    }

    /**
     * Calculates the minimum transactions needed to settle all debts.
     *
     * @param netBalances map of userId → net balance.
     *                    Positive = user is owed money (creditor).
     *                    Negative = user owes money (debtor).
     *                    All values must sum to zero (conservation of money).
     * @return list of settlement transactions (from → to, amount)
     * @throws IllegalArgumentException if balances don't sum to zero
     */
    public static List<SettlementTransaction> calculate(Map<UUID, BigDecimal> netBalances) {
        // ── 1. Validate: sum must be zero ────────────────────────────────────
        BigDecimal sum = netBalances.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "Net balances do not sum to zero (sum=" + sum + "). This indicates a bug in balance tracking.");
        }

        // ── 2. Separate into debtors and creditors ───────────────────────────
        //    Using a mutable wrapper so we can modify balances in-place.
        List<UserAmount> debtors = new ArrayList<>();   // negative balance → they owe
        List<UserAmount> creditors = new ArrayList<>(); // positive balance → they are owed

        for (var entry : netBalances.entrySet()) {
            int cmp = entry.getValue().compareTo(BigDecimal.ZERO);
            if (cmp < 0) {
                debtors.add(new UserAmount(entry.getKey(), entry.getValue().abs()));
            } else if (cmp > 0) {
                creditors.add(new UserAmount(entry.getKey(), entry.getValue()));
            }
            // cmp == 0 → fully settled, skip
        }

        // Sort: largest amounts first for both (greedy: settle big debts first)
        debtors.sort(Comparator.comparing(UserAmount::amount).reversed());
        creditors.sort(Comparator.comparing(UserAmount::amount).reversed());

        // ── 3. Two-pointer greedy matching ───────────────────────────────────
        List<SettlementTransaction> transactions = new ArrayList<>();
        int d = 0;  // debtor pointer
        int c = 0;  // creditor pointer

        while (d < debtors.size() && c < creditors.size()) {
            UserAmount debtor = debtors.get(d);
            UserAmount creditor = creditors.get(c);

            // Transfer the smaller of the two amounts
            BigDecimal transfer = debtor.amount().min(creditor.amount());

            transactions.add(new SettlementTransaction(
                    debtor.userId(), creditor.userId(), transfer));

            // Reduce both balances
            debtor.subtract(transfer);
            creditor.subtract(transfer);

            // Advance pointer(s) for fully settled parties
            if (debtor.amount().compareTo(BigDecimal.ZERO) == 0) {
                d++;
            }
            if (creditor.amount().compareTo(BigDecimal.ZERO) == 0) {
                c++;
            }
        }

        return Collections.unmodifiableList(transactions);
    }

    // ── Internal mutable wrapper ─────────────────────────────────────────────

    private static final class UserAmount {
        private final UUID userId;
        private BigDecimal amount;

        UserAmount(UUID userId, BigDecimal amount) {
            this.userId = userId;
            this.amount = amount;
        }

        UUID userId() { return userId; }
        BigDecimal amount() { return amount; }

        void subtract(BigDecimal value) {
            this.amount = this.amount.subtract(value);
        }
    }
}
