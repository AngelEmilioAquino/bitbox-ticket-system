package com.bitbox.inventory.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors. Each one carries the HTTP status and a stable,
 * machine-readable {@code code} that clients can rely on (the message is for humans).
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
