package com.stoxsim.analytics.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.repository.ActivityRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.domain.PlatformRole;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class ActivityAnalyticsServiceTest {
    @Mock AppUserRepository users;
    @Mock ActivityRepository activity;
    private ActivityAnalyticsService service;
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-21T12:00:00Z");
    private AppUser owner;
    @BeforeEach void setup() {
        service = new ActivityAnalyticsService(users, activity, Clock.fixed(now, ZoneOffset.ofHours(5)));
        owner = new AppUser("owner@test.example", "unused", "Owner");
        ReflectionTestUtils.setField(owner, "platformRole", PlatformRole.ADMIN);
    }
    @Test void deniesLearnersAndDeletedRequestersBeforeReadingActivity() {
        when(users.findById(id)).thenReturn(Optional.of(new AppUser("learner@test.example", "unused", "Learner")));
        assertThatThrownBy(() -> service.overview(id, null, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.overview(id, null, null)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(activity);
    }
    @Test void incompleteWindowsAndPretrackingDatesAreUnknownNotZero() {
        when(users.findById(id)).thenReturn(Optional.of(owner));
        when(activity.startedAt()).thenReturn(Instant.parse("2026-09-20T12:00:00Z"));
        var result = service.overview(id, LocalDate.of(2026,9,19), LocalDate.of(2026,9,21));
        assertThat(result.activeUsers().dau()).isZero();
        assertThat(result.activeUsers().wau()).isNull();
        assertThat(result.activeUsers().mau()).isNull();
        assertThat(result.daily().get(0).learners()).isNull();
        assertThat(result.daily().get(1).learners()).isNull();
        assertThat(result.daily().get(2).learners()).isZero();
        ReflectionTestUtils.setField(owner, "platformRole", PlatformRole.USER);
        assertThatThrownBy(() -> service.overview(id, null, null)).hasMessageContaining("403");
    }
    @Test void retentionExpiryMovesCoverageForwardAndHistoricWindowsStayUnknown() {
        when(users.findById(id)).thenReturn(Optional.of(owner));
        when(activity.startedAt()).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));
        var result = service.overview(id, LocalDate.of(2020,1,1), LocalDate.of(2020,1,2));
        assertThat(result.availableFrom()).isEqualTo(LocalDate.of(2026,9,21).minusDays(179).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(result.activeUsers().dau()).isNull();
        verify(activity, never()).active(any(), any());
    }
    @Test void rejectsInvalidDatesWithoutReadingAggregates() {
        when(users.findById(id)).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.overview(id, LocalDate.of(2026,9,22), null)).hasMessageContaining("400");
        assertThatThrownBy(() -> service.overview(id, LocalDate.of(2026,1,1), null)).hasMessageContaining("400");
        verifyNoInteractions(activity);
    }
}
