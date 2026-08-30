package com.stoxsim.campus.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.campus.service.CampusService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/campus")
public class CampusController {

    private final CampusService campus;

    public CampusController(CampusService campus) {
        this.campus = campus;
    }

    @GetMapping
    public CampusProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return campus.profile(userId(jwt));
    }

    @PostMapping("/verification-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CampusVerificationResponse requestVerification(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateCampusVerificationRequest request
    ) {
        return campus.submit(userId(jwt), request);
    }

    @GetMapping("/admin/verification-requests")
    public List<CampusVerificationResponse> pending(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return campus.pending(userId(jwt));
    }

    @PostMapping("/admin/verification-requests/{requestId}/approve")
    public CampusVerificationResponse approve(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID requestId,
        @Valid @RequestBody ReviewCampusVerificationRequest request
    ) {
        return campus.approve(userId(jwt), requestId, request.note());
    }

    @PostMapping("/admin/verification-requests/{requestId}/reject")
    public CampusVerificationResponse reject(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID requestId,
        @Valid @RequestBody ReviewCampusVerificationRequest request
    ) {
        return campus.reject(userId(jwt), requestId, request.note());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
