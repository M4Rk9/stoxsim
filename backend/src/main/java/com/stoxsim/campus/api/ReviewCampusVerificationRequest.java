package com.stoxsim.campus.api;

import jakarta.validation.constraints.Size;

public record ReviewCampusVerificationRequest(
    @Size(max = 500)
    String note
) {
}
