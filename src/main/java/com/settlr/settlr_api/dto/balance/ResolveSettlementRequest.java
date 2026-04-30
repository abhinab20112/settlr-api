package com.settlr.settlr_api.dto.balance;

import jakarta.validation.constraints.NotNull;

/**
 * Request to confirm or reject a pending settlement.
 */
public record ResolveSettlementRequest(
        @NotNull(message = "Confirm flag is required")
        Boolean confirm
) {}
