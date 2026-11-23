package com.bitbox.inventory.auth;

import com.bitbox.inventory.common.ApiException;
import org.springframework.http.HttpStatus;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static class EmailAlreadyRegisteredException extends ApiException {
        public EmailAlreadyRegisteredException() {
            super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "The email is already registered");
        }
    }

    /** Same message for unknown email and wrong password, to avoid user enumeration. */
    public static class InvalidCredentialsException extends ApiException {
        public InvalidCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
    }
}
