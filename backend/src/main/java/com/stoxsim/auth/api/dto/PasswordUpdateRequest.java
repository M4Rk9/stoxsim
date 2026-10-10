package com.stoxsim.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import com.stoxsim.auth.validation.Utf8Password;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
    @NotBlank @Utf8Password String currentPassword,
    @NotBlank @Size(min = 8, max = 72) @Utf8Password String newPassword
) {
}
