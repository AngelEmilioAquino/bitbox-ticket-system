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
     * Stock may still exist, but the purchase lost every optimistic-locking race within the retry budget.
     * Also 409, with a different code so clients know a retry may succeed.
     */
    public static class PurchaseConflictException extends ApiException {
        public PurchaseConflictException(Long eventId) {
            super(HttpStatus.CONFLICT, "PURCHASE_CONFLICT",
                    "High contention on event " + eventId + ", the purchase could not be completed. Please retry.");
        }
    }

    /** Valid signature but the user no longer exists (e.g. database was reset). */
    public static class BuyerNotFoundException extends ApiException {
        public BuyerNotFoundException() {
            super(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "The authenticated user does not exist");
        }
    }
}
