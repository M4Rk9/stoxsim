package com.stoxsim.campus.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.campus.api.CampusMembershipResponse;
import com.stoxsim.campus.api.CampusProfileResponse;
import com.stoxsim.campus.api.CampusVerificationResponse;
import com.stoxsim.campus.api.CreateCampusVerificationRequest;
import com.stoxsim.campus.domain.CampusInstitution;
import com.stoxsim.campus.domain.CampusMemberRole;
import com.stoxsim.campus.domain.CampusMembership;
import com.stoxsim.campus.domain.CampusRequestStatus;
import com.stoxsim.campus.domain.CampusVerificationRequest;
import com.stoxsim.campus.repository.CampusInstitutionRepository;
import com.stoxsim.campus.repository.CampusMembershipRepository;
import com.stoxsim.campus.repository.CampusVerificationRequestRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class CampusService {

    public static final String VERSION = "campus-verification-v1";

    private final AppUserRepository users;
    private final CampusInstitutionRepository institutions;
    private final CampusMembershipRepository memberships;
    private final CampusVerificationRequestRepository requests;
    private final AccountLifecycleService lifecycle;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public CampusService(
        AppUserRepository users,
        CampusInstitutionRepository institutions,
        CampusMembershipRepository memberships,
        CampusVerificationRequestRepository requests,
        AccountLifecycleService lifecycle,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        this.users = users;
        this.institutions = institutions;
        this.memberships = memberships;
        this.requests = requests;
        this.lifecycle = lifecycle;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public CampusProfileResponse profile(UUID userId) {
        AppUser user = requireUser(userId);
        CampusMembershipResponse membership = memberships.findByUserId(userId)
            .map(CampusMembershipResponse::from)
            .orElse(null);
        CampusVerificationResponse latest = requests
            .findFirstByRequester_IdOrderBySubmittedAtDesc(userId)
            .map(CampusVerificationResponse::from)
            .orElse(null);
        return new CampusProfileResponse(
            VERSION,
            user.isPlatformAdmin(),
            user.isEmailVerified(),
            membership,
            latest,
            "Institution approval verifies organizational identity only. Competition results remain educational and use the standard ₹5 lakh portfolio."
        );
    }

    @Transactional
    public CampusVerificationResponse submit(
        UUID userId,
        CreateCampusVerificationRequest request
    ) {
        AppUser user = requireUserForUpdate(userId);
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Verify your StoxSim email before requesting institution verification"
            );
        }
        if (memberships.existsByUser_Id(userId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "You already belong to a verified institution"
            );
        }
        var pending = requests.findFirstByRequester_IdOrderBySubmittedAtDesc(userId)
            .filter(existing -> existing.getStatus() == CampusRequestStatus.PENDING);
        if (pending.isPresent()) {
            return CampusVerificationResponse.from(pending.get());
        }

        String name = normalizeName(request.institutionName());
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String domain = request.emailDomain().trim().toLowerCase(Locale.ROOT);
        if (institutions.existsByNormalizedName(normalizedName)
            || institutions.existsByEmailDomain(domain)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This institution is already verified"
            );
        }
        CampusVerificationRequest saved = requests.save(new CampusVerificationRequest(
            user,
            name,
            normalizedName,
            domain,
            normalizeWebsite(request.websiteUrl()),
            clock.instant()
        ));
        lifecycle.audit(userId, "CAMPUS_VERIFICATION_REQUESTED", saved.getId().toString());
        meterRegistry.counter("stoxsim.campus.verification", "result", "requested")
            .increment();
        return CampusVerificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CampusVerificationResponse> pending(UUID adminUserId) {
        requireAdmin(adminUserId);
        return requests.findAllByStatus(CampusRequestStatus.PENDING)
            .stream()
            .map(CampusVerificationResponse::from)
            .toList();
    }

    @Transactional
    public CampusVerificationResponse approve(
        UUID adminUserId,
        UUID requestId,
        String requestedNote
    ) {
        AppUser admin = requireAdmin(adminUserId);
        CampusVerificationRequest request = pendingRequest(requestId);
        if (memberships.existsByUser_Id(request.getRequester().getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The requester already belongs to a verified institution"
            );
        }
        if (institutions.existsByNormalizedName(request.getNormalizedName())
            || institutions.existsByEmailDomain(request.getEmailDomain())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An institution with this name or email domain is already verified"
            );
        }

        Instant now = clock.instant();
        CampusInstitution institution = institutions.save(new CampusInstitution(
            request.getInstitutionName(),
            request.getNormalizedName(),
            request.getEmailDomain(),
            request.getWebsiteUrl(),
            admin,
            now
        ));
        memberships.save(new CampusMembership(
            institution,
            request.getRequester(),
            CampusMemberRole.ORGANIZER,
            now
        ));
        request.approve(admin, normalizeNote(requestedNote), now);
        auditReview(adminUserId, request, "approved");
        return CampusVerificationResponse.from(request);
    }

    @Transactional
    public CampusVerificationResponse reject(
        UUID adminUserId,
        UUID requestId,
        String requestedNote
    ) {
        AppUser admin = requireAdmin(adminUserId);
        String note = normalizeNote(requestedNote);
        if (!StringUtils.hasText(note)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A rejection note is required"
            );
        }
        CampusVerificationRequest request = pendingRequest(requestId);
        request.reject(admin, note, clock.instant());
        auditReview(adminUserId, request, "rejected");
        return CampusVerificationResponse.from(request);
    }

    private void auditReview(
        UUID adminUserId,
        CampusVerificationRequest request,
        String result
    ) {
        lifecycle.audit(
            adminUserId,
            "CAMPUS_VERIFICATION_REVIEWED",
            request.getId() + ":" + result
        );
        lifecycle.audit(
            request.getRequester().getId(),
            "CAMPUS_VERIFICATION_" + result.toUpperCase(Locale.ROOT),
            request.getId().toString()
        );
        meterRegistry.counter("stoxsim.campus.verification", "result", result)
            .increment();
    }

    private CampusVerificationRequest pendingRequest(UUID requestId) {
        CampusVerificationRequest request = requests.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campus verification request not found"
            ));
        if (request.getStatus() != CampusRequestStatus.PENDING) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Campus verification request is already reviewed"
            );
        }
        return request;
    }

    private AppUser requireAdmin(UUID userId) {
        AppUser user = requireUser(userId);
        if (!user.isPlatformAdmin()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Platform administrator access is required"
            );
        }
        return user;
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private AppUser requireUserForUpdate(UUID userId) {
        return users.findByIdForUpdate(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeWebsite(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeNote(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
