package com.stoxsim.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuthResponse(
    String accessToken,
    @JsonIgnore String refreshToken,
    String tokenType,
    long expiresInSeconds,
    UserResponse user
) {
}
