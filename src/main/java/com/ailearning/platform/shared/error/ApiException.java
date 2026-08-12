package com.ailearning.platform.shared.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
