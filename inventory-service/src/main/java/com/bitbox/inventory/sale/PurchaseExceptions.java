package com.bitbox.inventory.sale;

import com.bitbox.inventory.common.ApiException;
import org.springframework.http.HttpStatus;

public final class PurchaseExceptions {

    private PurchaseExceptions() {
    }

    /** No stock left: the definitive "no" required by the spec (409). */
    public static class SoldOutException extends ApiException {
        public SoldOutException(Long eventId) {
            super(HttpStatus.CONFLICT, "SOLD_OUT", "No tickets available for event " + eventId);
        }
    }

    /**
     * The database could not complete the purchase because of a transient concurrency failure
     * (lock timeout, deadlock...). Nothing was sold; stock may still be available, so the client may retry.
     */
    public static class PurchaseTemporarilyUnavailableException extends ApiException {
        public PurchaseTemporarilyUnavailableException(Long eventId, Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "PURCHASE_TEMPORARILY_UNAVAILABLE",
                    "The purchase for event " + eventId + " could not be completed due to high contention. Please retry.");
            initCause(cause);
        }
    }

    /** Valid signature but the user no longer exists (e.g. database was reset). */
    public static class BuyerNotFoundException extends ApiException {
        public BuyerNotFoundException() {
            super(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "The authenticated user does not exist");
        }
    }
}
