package com.settlr.settlr_api.exception;

public class GroqException extends RuntimeException {
    public GroqException(String message) {
        super(message);
    }

    public GroqException(String message, Throwable cause) {
        super(message, cause);
    }
}
