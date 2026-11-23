package com.bitbox.inventory.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "ana@bitbox.com") @NotBlank String email,
        @Schema(example = "S3cret-pass") @NotBlank String password) {
}
