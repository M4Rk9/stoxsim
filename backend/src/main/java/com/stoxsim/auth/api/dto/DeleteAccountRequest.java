package com.stoxsim.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
    @NotBlank String password
) {
}
