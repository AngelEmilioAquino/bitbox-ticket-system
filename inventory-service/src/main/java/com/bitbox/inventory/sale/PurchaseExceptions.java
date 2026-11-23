package com.bitbox.inventory.sale;

import com.bitbox.inventory.common.BusinessException;

public final class PurchaseExceptions {

    private PurchaseExceptions() {
    }

    /** Real lack of stock: the definitive "no". */
    public static class SoldOutException extends BusinessException {
        public SoldOutException(Long eventId) {
            super("SOLD_OUT", "No tickets available for event " + eventId);
        }
    }

    /**
     * The database could not complete the purchase because of a transient concurrency failure
     * (lock timeout, deadlock...). Nothing was sold; stock may still be available, so the client may retry.
     */
    public static class PurchaseTemporarilyUnavailableException extends BusinessException {
        public PurchaseTemporarilyUnavailableException(Long eventId, Throwable cause) {
            super("PURCHASE_TEMPORARILY_UNAVAILABLE",
                    "The purchase for event " + eventId + " could not be completed due to high contention. Please retry.",
                    cause);
        }
    }

    /** Valid signature but the user no longer exists (e.g. database was reset). */
    public static class BuyerNotFoundException extends BusinessException {
        public BuyerNotFoundException() {
            super("USER_NOT_FOUND", "The authenticated user does not exist");
        }
    }
}
