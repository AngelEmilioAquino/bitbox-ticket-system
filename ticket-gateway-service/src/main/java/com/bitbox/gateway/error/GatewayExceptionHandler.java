package com.bitbox.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /** Relays inventory-service errors with their original status and body. */
    @ExceptionHandler(DownstreamException.class)
    public ResponseEntity<?> handleDownstream(DownstreamException ex) {
        if (ex.getBody() == null || ex.getBody().isBlank()) {
            // e.g. 401 from inventory's security filter, which has no body
            HttpStatus status = HttpStatus.valueOf(ex.getStatus().value());
            return ResponseEntity.status(status).body(problem(status, status.name(), status.getReasonPhrase()));
        }
        MediaType contentType = ex.getContentType() != null ? ex.getContentType() : MediaType.APPLICATION_PROBLEM_JSON;
        return ResponseEntity.status(ex.getStatus()).contentType(contentType).body(ex.getBody());
    }

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ProblemDetail> handleGateway(GatewayException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("{}: {}", ex.getCode(), ex.getCause() != null ? ex.getCause().toString() : ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(problem(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
