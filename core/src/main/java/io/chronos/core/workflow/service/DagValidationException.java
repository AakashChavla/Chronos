package io.chronos.core.workflow.service;

public final class DagValidationException extends RuntimeException {
    private final String code;
    private final String stepKey;

    public DagValidationException(String code, String stepKey, String message) {
        super(message);
        this.code = code;
        this.stepKey = stepKey;
    }

    public String code() {
        return code;
    }

    public String stepKey() {
        return stepKey;
    }
}
