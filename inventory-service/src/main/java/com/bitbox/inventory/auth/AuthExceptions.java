package com.bitbox.inventory.auth;

import com.bitbox.inventory.common.BusinessException;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static class EmailAlreadyRegisteredException extends BusinessException {
        public EmailAlreadyRegisteredException() {
            super("EMAIL_ALREADY_REGISTERED", "The email is already registered");
        }
    }

    /** Same message for unknown email and wrong password, to avoid user enumeration. */
    public static class InvalidCredentialsException extends BusinessException {
        public InvalidCredentialsException() {
            super("INVALID_CREDENTIALS", "Invalid email or password");
        }
    }
}
