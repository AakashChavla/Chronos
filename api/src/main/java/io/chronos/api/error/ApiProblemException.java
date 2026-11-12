package io.chronos.api.error;

public final class ApiProblemException extends RuntimeException {
    private final String code;

    public ApiProblemException(String code, String detail) {
        super(detail);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
