package com.bitbox.gateway.error;

import org.springframework.http.HttpStatus;

/** Errors raised by the gateway itself (as opposed to errors relayed from inventory-service). */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public GatewayException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static GatewayException missingBearerToken() {
        return new GatewayException(HttpStatus.UNAUTHORIZED, "MISSING_BEARER_TOKEN",
                "An 'Authorization: Bearer <token>' header is required", null);
    }

    public static GatewayException inventoryUnavailable(Throwable cause) {
        return new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_UNAVAILABLE",
                "inventory-service is not reachable", cause);
    }

    public static GatewayException inventoryTimeout(Throwable cause) {
        return new GatewayException(HttpStatus.GATEWAY_TIMEOUT, "INVENTORY_TIMEOUT",
                "inventory-service did not answer in time", cause);
    }
}
