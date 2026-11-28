package com.stoxsim.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.stoxsim.auth.validation.Utf8Password;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 72) @Utf8Password String password,
    @NotBlank @Size(min = 2, max = 100) String displayName,
    @AssertTrue(message = "You must accept the Terms and Privacy Notice") boolean termsAccepted,
    @Size(max = 24) String signupSource,
    @Size(max = 32) String referralCode
) {
    public RegisterRequest(String email, String password, String displayName, boolean termsAccepted) {
        this(email, password, displayName, termsAccepted, null, null);
    }
}
