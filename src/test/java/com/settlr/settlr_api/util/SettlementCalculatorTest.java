package com.settlr.settlr_api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SettlementCalculatorTest {

    // Helper: quick UUID generator
    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    // Helper: assert that all debts are fully settled (net of transactions = original balances)
    private void assertFullySettled(Map<UUID, BigDecimal> balances, List<SettlementTransaction> txns) {
        // Build a net map from transactions
        Map<UUID, BigDecimal> netFromTxns = new HashMap<>();
        for (SettlementTransaction t : txns) {
            netFromTxns.merge(t.fromUserId(), t.amount().negate(), BigDecimal::add); // payer loses
            netFromTxns.merge(t.toUserId(), t.amount(), BigDecimal::add);            // receiver gains
        }

        // Every non-zero balance should be accounted for
        for (var entry : balances.entrySet()) {
            BigDecimal expected = entry.getValue();
            BigDecimal actual = netFromTxns.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            assertEquals(0, expected.compareTo(actual),
                    "User " + entry.getKey() + " not fully settled: expected=" + expected + ", actual=" + actual);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 1: Simple two-person split
    // Alice paid 600, split with Bob → Alice is owed 300, Bob owes 300
    // Expected: 1 transaction → Bob pays Alice 300
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 1: Simple two-person — one transaction")
    void simpleTwoPerson() {
        UUID alice = id(1);
        UUID bob = id(2);

        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, new BigDecimal("300"));    // owed 300
        balances.put(bob, new BigDecimal("-300"));      // owes 300

        List<SettlementTransaction> result = SettlementCalculator.calculate(balances);

        assertEquals(1, result.size(), "Should produce exactly 1 transaction");

        SettlementTransaction txn = result.get(0);
        assertEquals(bob, txn.fromUserId());
        assertEquals(alice, txn.toUserId());
        assertEquals(0, new BigDecimal("300").compareTo(txn.amount()));

        assertFullySettled(balances, result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 2: Three people, unequal debts
    // Alice is owed 400, Bob owes 150, Charlie owes 250
    // Expected: 2 transactions (minimum possible for 3 people)
    //   Charlie → Alice: 250
    //   Bob → Alice: 150
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 2: Three people, unequal debts — two transactions")
    void threePeopleUnequal() {
        UUID alice = id(1);
        UUID bob = id(2);
        UUID charlie = id(3);

        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, new BigDecimal("400"));
        balances.put(bob, new BigDecimal("-150"));
        balances.put(charlie, new BigDecimal("-250"));

        List<SettlementTransaction> result = SettlementCalculator.calculate(balances);

        assertEquals(2, result.size(), "Should produce exactly 2 transactions");
        assertFullySettled(balances, result);

        // Total transferred should equal 400
        BigDecimal totalTransferred = result.stream()
                .map(SettlementTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("400").compareTo(totalTransferred));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 3: Circular debt — A owes B, B owes C, C owes A
    // Net: Alice paid for B and C, B paid for C and A, C paid for A and B
    //
    // Alice: net = +100 (owed 100)
    // Bob:   net = -200 (owes 200)
    // Charlie: net = +100 (owed 100)
    //
    // Without the algorithm, you'd need 3 circular transfers.
    // With min-cash-flow: Bob → Alice 100, Bob → Charlie 100 = 2 transactions.
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 3: Circular debt — collapses to 2 direct transfers")
    void circularDebt() {
        UUID alice = id(1);
        UUID bob = id(2);
        UUID charlie = id(3);

        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, new BigDecimal("100"));
        balances.put(bob, new BigDecimal("-200"));
        balances.put(charlie, new BigDecimal("100"));

        List<SettlementTransaction> result = SettlementCalculator.calculate(balances);

        // Circular debt should collapse: max 2 transactions instead of 3
        assertTrue(result.size() <= 2, "Circular debt should need at most 2 transactions, got " + result.size());
        assertFullySettled(balances, result);

        // Every transaction should have Bob as the payer (he's the only debtor)
        for (SettlementTransaction txn : result) {
            assertEquals(bob, txn.fromUserId(), "Bob should be the only payer");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 4: All settled (all balances are zero)
    // Expected: empty list
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 4: All already settled — zero transactions")
    void allSettled() {
        UUID alice = id(1);
        UUID bob = id(2);

        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, BigDecimal.ZERO);
        balances.put(bob, BigDecimal.ZERO);

        List<SettlementTransaction> result = SettlementCalculator.calculate(balances);

        assertTrue(result.isEmpty(), "Fully settled group should produce no transactions");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 5: Five people — complex multi-party
    // A: +500, B: -200, C: -100, D: +50, E: -250
    // Should produce at most 4 transactions (N-1)
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 5: Five people — at most N-1 transactions")
    void fivePeopleComplex() {
        UUID a = id(1), b = id(2), c = id(3), d = id(4), e = id(5);

        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(a, new BigDecimal("500"));
        balances.put(b, new BigDecimal("-200"));
        balances.put(c, new BigDecimal("-100"));
        balances.put(d, new BigDecimal("50"));
        balances.put(e, new BigDecimal("-250"));

        List<SettlementTransaction> result = SettlementCalculator.calculate(balances);

        assertTrue(result.size() <= 4, "5 people should need at most 4 transactions, got " + result.size());
        assertFullySettled(balances, result);

        // All amounts must be positive
        for (SettlementTransaction txn : result) {
            assertTrue(txn.amount().compareTo(BigDecimal.ZERO) > 0,
                    "Transaction amount must be positive: " + txn.amount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 6: Invalid input — balances don't sum to zero
    // Expected: IllegalArgumentException
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 6: Invalid input — throws if balances don't sum to zero")
    void invalidBalances() {
        UUID alice = id(1);
        UUID bob = id(2);

        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, new BigDecimal("300"));
        balances.put(bob, new BigDecimal("-100"));  // sum = 200, not zero

        assertThrows(IllegalArgumentException.class,
                () -> SettlementCalculator.calculate(balances));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scenario 7: Empty input
    // Expected: empty list
    // ═══════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Scenario 7: Empty input — zero transactions")
    void emptyInput() {
        List<SettlementTransaction> result = SettlementCalculator.calculate(Map.of());
        assertTrue(result.isEmpty());
    }
}
