package com.stoxsim.auth.api.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    UserResponse user
) {
}
