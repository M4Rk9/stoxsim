package com.stoxsim.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import com.stoxsim.auth.validation.Utf8Password;

public record DeleteAccountRequest(
    @NotBlank @Utf8Password String password
) {
}
