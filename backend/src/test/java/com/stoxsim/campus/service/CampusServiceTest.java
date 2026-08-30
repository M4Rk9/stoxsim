package com.stoxsim.campus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.domain.PlatformRole;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.campus.api.CreateCampusVerificationRequest;
import com.stoxsim.campus.domain.CampusInstitution;
import com.stoxsim.campus.domain.CampusMembership;
import com.stoxsim.campus.domain.CampusRequestStatus;
import com.stoxsim.campus.domain.CampusVerificationRequest;
import com.stoxsim.campus.repository.CampusInstitutionRepository;
import com.stoxsim.campus.repository.CampusMembershipRepository;
import com.stoxsim.campus.repository.CampusVerificationRequestRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class CampusServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-30T09:00:00Z");

    @Mock private AppUserRepository users;
    @Mock private CampusInstitutionRepository institutions;
    @Mock private CampusMembershipRepository memberships;
    @Mock private CampusVerificationRequestRepository requests;
    @Mock private AccountLifecycleService lifecycle;

    private AppUser user;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        user = user(USER_ID, "student@example.com", PlatformRole.USER);
        admin = user(ADMIN_ID, "admin@stoxsim.com", PlatformRole.ADMIN);
    }

    @Test
    void verifiedLearnerCanSubmitANormalizedInstitutionRequest() {
        user.markEmailVerified();
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(requests.findFirstByRequester_IdOrderBySubmittedAtDesc(USER_ID))
            .thenReturn(Optional.empty());
        when(requests.save(any(CampusVerificationRequest.class)))
            .thenAnswer(invocation -> {
                CampusVerificationRequest saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", REQUEST_ID);
                return saved;
            });

        var response = service().submit(USER_ID, new CreateCampusVerificationRequest(
            "  Birla   Institute of Technology, Mesra ",
            "BITMESRA.AC.IN",
            "https://www.bitmesra.ac.in"
        ));

        assertThat(response.institutionName())
            .isEqualTo("Birla Institute of Technology, Mesra");
        assertThat(response.emailDomain()).isEqualTo("bitmesra.ac.in");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(lifecycle).audit(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.eq("CAMPUS_VERIFICATION_REQUESTED"),
            any()
        );
    }

    @Test
    void unverifiedLearnerCannotSubmitAnInstitutionRequest() {
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().submit(
            USER_ID,
            request()
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Verify your StoxSim email");

        verify(requests, never()).save(any());
    }

    @Test
    void nonAdminCannotReadTheModerationQueue() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().pending(USER_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("administrator access");
    }

    @Test
    void adminApprovalCreatesAOneInstitutionOrganizerMembership() {
        CampusVerificationRequest pending = pendingRequest();
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(pending));
        when(institutions.save(any(CampusInstitution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberships.save(any(CampusMembership.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().approve(ADMIN_ID, REQUEST_ID, "Official site confirmed");

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewNote()).isEqualTo("Official site confirmed");
        var membership = org.mockito.ArgumentCaptor.forClass(CampusMembership.class);
        verify(memberships).save(membership.capture());
        assertThat(membership.getValue().getRole().name()).isEqualTo("ORGANIZER");
        verify(lifecycle).audit(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.eq("CAMPUS_VERIFICATION_APPROVED"),
            org.mockito.ArgumentMatchers.eq(REQUEST_ID.toString())
        );
    }

    @Test
    void rejectionRequiresAnOperatorNote() {
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service().reject(ADMIN_ID, REQUEST_ID, " "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("rejection note is required");

        verify(requests, never()).findByIdForUpdate(any());
    }

    private CampusService service() {
        return new CampusService(
            users,
            institutions,
            memberships,
            requests,
            lifecycle,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry()
        );
    }

    private CampusVerificationRequest pendingRequest() {
        CampusVerificationRequest request = new CampusVerificationRequest(
            user,
            "Birla Institute of Technology, Mesra",
            "birla institute of technology, mesra",
            "bitmesra.ac.in",
            "https://www.bitmesra.ac.in",
            NOW.minusSeconds(3600)
        );
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        return request;
    }

    private CreateCampusVerificationRequest request() {
        return new CreateCampusVerificationRequest(
            "Birla Institute of Technology, Mesra",
            "bitmesra.ac.in",
            "https://www.bitmesra.ac.in"
        );
    }

    private AppUser user(UUID id, String email, PlatformRole role) {
        AppUser result = new AppUser(email, "hash", "Campus User");
        ReflectionTestUtils.setField(result, "id", id);
        ReflectionTestUtils.setField(result, "platformRole", role);
        return result;
    }
}
