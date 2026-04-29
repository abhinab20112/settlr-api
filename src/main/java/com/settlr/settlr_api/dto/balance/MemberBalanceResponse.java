package com.settlr.settlr_api.dto.balance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Net balance of a single member in a group.
 *
 * Positive netBalance → this member is owed money (creditor).
 * Negative netBalance → this member owes money (debtor).
 * Zero               → fully settled.
 */
public record MemberBalanceResponse(
        UUID userId,
        String name,
        String email,
        BigDecimal netBalance
) {}
