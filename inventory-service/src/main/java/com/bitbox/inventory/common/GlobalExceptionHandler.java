package com.bitbox.inventory.common;

import java.util.LinkedHashMap;
import java.util.Map;

import com.bitbox.inventory.auth.AuthExceptions.EmailAlreadyRegisteredException;
import com.bitbox.inventory.auth.AuthExceptions.InvalidCredentialsException;
import com.bitbox.inventory.event.EventNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.BuyerNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.PurchaseTemporarilyUnavailableException;
import com.bitbox.inventory.sale.PurchaseExceptions.SoldOutException;
import com.bitbox.inventory.security.AuthenticatedUser.InvalidTokenSubjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place where business exceptions are translated to HTTP. Business code throws transport-agnostic
 * {@link BusinessException}s; this class decides the status and renders an RFC 7807 {@link ProblemDetail}
 * with the exception's stable {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Seconds a client should wait before retrying a purchase that failed for transient reasons. */
    static final String RETRY_AFTER_SECONDS = "1";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(BusinessException ex) {
        return respond(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler({SoldOutException.class, EmailAlreadyRegisteredException.class})
    public ResponseEntity<ProblemDetail> handleConflict(BusinessException ex) {
        return respond(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler({InvalidCredentialsException.class, BuyerNotFoundException.class,
            InvalidTokenSubjectException.class})
    public ResponseEntity<ProblemDetail> handleUnauthenticated(BusinessException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex);
    }

    /** Transient: nothing was sold and the client may retry (different from SOLD_OUT, which is final). */
    @ExceptionHandler(PurchaseTemporarilyUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleTransient(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getCode(), ex.getMessage()));
    }

    /** Safety net for a business exception that was not mapped above: a client error, never a 500. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleUnmapped(BusinessException ex) {
        log.warn("Unmapped business exception {} ({})", ex.getClass().getName(), ex.getCode());
        return respond(HttpStatus.BAD_REQUEST, ex);
    }

    /**
     * Last line of defence: a DB constraint (unique email, stock CHECK) rejected the write,
     * e.g. two concurrent registrations with the same email.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION", "The request conflicts with the current state of the data"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, BusinessException ex) {
        return ResponseEntity.status(status).body(problem(status, ex.getCode(), ex.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
