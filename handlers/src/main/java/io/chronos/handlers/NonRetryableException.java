package io.chronos.handlers;

public final class NonRetryableException extends RuntimeException {
    public NonRetryableException(String message) {
        super(message);
    }
}
