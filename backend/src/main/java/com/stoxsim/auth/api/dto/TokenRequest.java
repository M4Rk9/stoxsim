package com.stoxsim.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
    @NotBlank String token
) {
}
