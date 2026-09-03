package com.ailearning.platform.sharedkernel.error;

public class BusinessException extends RuntimeException {
    private final String code;
    private final ErrorType type;

    public BusinessException(String code, ErrorType type, String message) {
        super(message);
        this.code = code;
        this.type = type;
    }

    public BusinessException(String code, ErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.type = type;
    }

    public String code() {
        return code;
    }

    public ErrorType type() {
        return type;
    }
}
