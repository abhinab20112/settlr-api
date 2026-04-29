package com.settlr.settlr_api.util;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents a single settlement transaction in the min-cash-flow output.
 *
 * @param fromUserId the user who should pay
 * @param toUserId   the user who should receive
 * @param amount     how much to transfer (always positive)
 */
public record SettlementTransaction(
        UUID fromUserId,
        UUID toUserId,
        BigDecimal amount
) {}
