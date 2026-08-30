package com.stoxsim.campus.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCampusVerificationRequest(
    @NotBlank
    @Size(min = 3, max = 160)
    String institutionName,

    @NotBlank
    @Size(max = 190)
    @Pattern(
        regexp = "(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$",
        message = "must be a valid institution email domain"
    )
    String emailDomain,

    @Size(max = 300)
    @Pattern(
        regexp = "^$|^https://[^\\s]+$",
        message = "must be an HTTPS URL"
    )
    String websiteUrl
) {
}
