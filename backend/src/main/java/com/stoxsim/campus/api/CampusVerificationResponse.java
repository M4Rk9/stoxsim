package com.stoxsim.campus.api;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.campus.domain.CampusVerificationRequest;

public record CampusVerificationResponse(
    UUID id,
    String institutionName,
    String emailDomain,
    String websiteUrl,
    String status,
    String requesterDisplayName,
    String requesterEmail,
    Instant submittedAt,
    Instant reviewedAt,
    String reviewNote
) {
    public static CampusVerificationResponse from(
        CampusVerificationRequest request
    ) {
        return new CampusVerificationResponse(
            request.getId(),
            request.getInstitutionName(),
            request.getEmailDomain(),
            request.getWebsiteUrl(),
            request.getStatus().name(),
            request.getRequester().getDisplayName(),
            request.getRequester().getEmail(),
            request.getSubmittedAt(),
            request.getReviewedAt(),
            request.getReviewNote()
        );
    }
}
