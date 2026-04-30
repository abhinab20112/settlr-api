package com.settlr.settlr_api.entity;

/**
 * Lifecycle of a settlement:
 *   PENDING   → Bob says "I paid Alice ₹2300" — waiting for Alice to confirm.
 *   CONFIRMED → Alice confirms receipt — balances are updated.
 *   REJECTED  → Alice rejects — no balance change, Bob can re-initiate.
 */
public enum SettlementStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
