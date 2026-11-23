package com.bitbox.inventory.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.bitbox.inventory.auth.AuthExceptions.EmailAlreadyRegisteredException;
import com.bitbox.inventory.auth.AuthExceptions.InvalidCredentialsException;
import com.bitbox.inventory.event.EventNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.BuyerNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.PurchaseTemporarilyUnavailableException;
import com.bitbox.inventory.sale.PurchaseExceptions.SoldOutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** The HTTP translation of every business exception, in one place. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessExceptionsToHttpStatuses() {
        assertProblem(handler.handleNotFound(new EventNotFoundException(1L)), 404, "EVENT_NOT_FOUND");
        assertProblem(handler.handleConflict(new SoldOutException(1L)), 409, "SOLD_OUT");
        assertProblem(handler.handleConflict(new EmailAlreadyRegisteredException()), 409, "EMAIL_ALREADY_REGISTERED");
        assertProblem(handler.handleUnauthenticated(new InvalidCredentialsException()), 401, "INVALID_CREDENTIALS");
        assertProblem(handler.handleUnauthenticated(new BuyerNotFoundException()), 401, "USER_NOT_FOUND");
    }

    @Test
    void transientPurchaseFailureIsRetryable503() {
        ResponseEntity<ProblemDetail> response = handler.handleTransient(
                new PurchaseTemporarilyUnavailableException(1L, new RuntimeException("lock timeout")));

        assertProblem(response, 503, "PURCHASE_TEMPORARILY_UNAVAILABLE");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    void unmappedBusinessExceptionIsAClientErrorNotA500() {
        BusinessException unmapped = new BusinessException("SOMETHING_NEW", "new rule") { };

        assertProblem(handler.handleUnmapped(unmapped), 400, "SOMETHING_NEW");
    }

    private static void assertProblem(ResponseEntity<ProblemDetail> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", code);
    }
}
