package com.bitbox.inventory.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "ana@bitbox.com")
        @NotBlank @Email @Size(max = 255) String email,

        /* BCrypt only uses the first 72 bytes of the input, so longer passwords are rejected explicitly. */
        @Schema(example = "S3cret-pass", minLength = 8, maxLength = 72)
        @NotBlank @Size(min = 8, max = 72) String password) {
}
