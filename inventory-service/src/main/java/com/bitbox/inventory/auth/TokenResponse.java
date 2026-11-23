package com.bitbox.inventory.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
