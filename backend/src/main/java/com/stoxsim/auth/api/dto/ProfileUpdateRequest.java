package com.stoxsim.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 2, max = 100) String displayName,
    @Size(max = 72) String currentPassword
) {
    public ProfileUpdateRequest(String email, String displayName) {
        this(email, displayName, null);
    }
}
