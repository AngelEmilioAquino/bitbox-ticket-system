package com.bitbox.inventory.security;

import com.bitbox.inventory.common.BusinessException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the buyer identity from an already-validated JWT. */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static Long idOf(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException ex) {
            throw new InvalidTokenSubjectException();
        }
    }

    public static class InvalidTokenSubjectException extends BusinessException {
        public InvalidTokenSubjectException() {
            super("INVALID_TOKEN_SUBJECT", "The token does not identify a valid user");
        }
    }
}
