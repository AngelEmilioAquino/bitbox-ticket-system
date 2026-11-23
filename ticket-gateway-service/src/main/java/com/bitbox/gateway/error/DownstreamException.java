package com.bitbox.gateway.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/**
 * An HTTP error answered by inventory-service (400, 401, 404, 409...). The gateway relays it as-is
 * (status + body) instead of turning every downstream failure into a generic 500.
 */
public class DownstreamException extends RuntimeException {

    private final HttpStatusCode status;
    private final String body;
    private final MediaType contentType;

    public DownstreamException(HttpStatusCode status, String body, MediaType contentType) {
        super("inventory-service responded " + status.value());
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public MediaType getContentType() {
        return contentType;
    }
}
