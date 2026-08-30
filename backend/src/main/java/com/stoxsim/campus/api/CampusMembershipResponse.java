package com.stoxsim.campus.api;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.campus.domain.CampusMembership;

public record CampusMembershipResponse(
    UUID institutionId,
    String institutionName,
    String emailDomain,
    String websiteUrl,
    String role,
    Instant institutionVerifiedAt,
    Instant joinedAt
) {
    public static CampusMembershipResponse from(CampusMembership membership) {
        var institution = membership.getInstitution();
        return new CampusMembershipResponse(
            institution.getId(),
            institution.getName(),
            institution.getEmailDomain(),
            institution.getWebsiteUrl(),
            membership.getRole().name(),
            institution.getVerifiedAt(),
            membership.getJoinedAt()
        );
    }
}
