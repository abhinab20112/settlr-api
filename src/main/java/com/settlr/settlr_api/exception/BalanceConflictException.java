package com.settlr.settlr_api.exception;

/**
 * Thrown when an optimistic lock conflict occurs on UserBalance
 * (i.e. @Version mismatch due to concurrent update).
 *
 * This is a retryable exception — callers should catch it and retry
 * the entire transaction.
 */
public class BalanceConflictException extends RuntimeException {

    public BalanceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
