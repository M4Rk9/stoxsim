package com.stoxsim.campus.api;

public record CampusProfileResponse(
    String version,
    boolean platformAdmin,
    boolean emailVerified,
    CampusMembershipResponse membership,
    CampusVerificationResponse latestVerificationRequest,
    String notice
) {
}
