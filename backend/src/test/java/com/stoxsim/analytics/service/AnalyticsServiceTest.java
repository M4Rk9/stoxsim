package com.stoxsim.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.analytics.api.AnalyticsOverview.DailySignups;
import com.stoxsim.analytics.api.AnalyticsOverview.SignupCohort;
import com.stoxsim.analytics.api.AnalyticsOverview.UserTotals;
import com.stoxsim.analytics.repository.AnalyticsRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.domain.PlatformRole;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    @Mock private AppUserRepository users;
    @Mock private AnalyticsRepository analytics;
    private AnalyticsService service;
    private AppUser admin;

    @BeforeEach
    void setup() {
        // Non-UTC injected zone proves reporting does not inherit server/clock local dates.
        service = new AnalyticsService(users, analytics, Clock.fixed(NOW, ZoneOffset.ofHours(-7)));
        admin = new AppUser("owner@stoxsim.test", "unused", "Owner");
        ReflectionTestUtils.setField(admin, "platformRole", PlatformRole.ADMIN);
    }

    @Test
    void rejectsLearnerBeforeReadingAnyAggregates() {
        when(users.findById(ID)).thenReturn(Optional.of(new AppUser("user@stoxsim.test", "unused", "User")));
        assertThatThrownBy(() -> service.overview(ID, null, null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(analytics);
    }

    @Test
    void deletedRequesterIsUnauthorized() {
        when(users.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.overview(ID, null, null)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(analytics);
    }

    @Test
    void rejectsReversedFutureAndOverlongRangesBeforeQuerying() {
        when(users.findById(ID)).thenReturn(Optional.of(admin));
        LocalDate today = LocalDate.of(2026, 9, 21);
        for (LocalDate[] range : List.of(
            new LocalDate[] {today, today.minusDays(1)},
            new LocalDate[] {today, today.plusDays(1)},
            new LocalDate[] {today.minusDays(90), today},
            new LocalDate[] {LocalDate.of(1969, 12, 31), LocalDate.of(1970, 1, 1)}
        )) {
            assertThatThrownBy(() -> service.overview(ID, range[0], range[1]))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
        verifyNoInteractions(analytics);
    }

    @Test
    void defaultsToThirtyUtcDaysAndFillsMissingDaysWithZero() {
        allowOverview();
        when(analytics.signups(any(), any())).thenReturn(List.of(
            new DailySignups(LocalDate.of(2026, 9, 1), 3)
        ));
        var response = service.overview(ID, null, null);
        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(response.to()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(response.timeZone()).isEqualTo("UTC");
        assertThat(response.signups()).hasSize(30);
        assertThat(response.signups().stream().mapToLong(DailySignups::registered).sum()).isEqualTo(3);
        assertThat(response.signups().getFirst().registered()).isZero();
        verify(analytics).cohort(Instant.parse("2026-08-23T00:00:00Z"), NOW, NOW);
        verify(analytics).orders(Instant.parse("2026-08-23T00:00:00Z"), NOW);
    }

    @Test
    void permitsExactlyNinetyDaysAndUsesExclusiveMidnightForHistoricalEnd() {
        allowOverview();
        var last = LocalDate.of(2026, 9, 20);
        var response = service.overview(ID, last.minusDays(89), last);
        assertThat(response.signups()).hasSize(90);
        verify(analytics).orders(last.minusDays(89).atStartOfDay(ZoneOffset.UTC).toInstant(),
            Instant.parse("2026-09-21T00:00:00Z"));
    }

    @Test
    void acceptsASingleDayAndRechecksRevokedAdministratorRole() {
        allowOverview();
        var day = LocalDate.of(2026, 9, 20);
        assertThat(service.overview(ID, day, day).signups()).hasSize(1);
        ReflectionTestUtils.setField(admin, "platformRole", PlatformRole.USER);
        assertThatThrownBy(() -> service.overview(ID, day, day))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    private void allowOverview() {
        when(users.findById(ID)).thenReturn(Optional.of(admin));
        when(analytics.users(any())).thenReturn(new UserTotals(0, 0));
        when(analytics.cohort(any(), any(), any())).thenReturn(new SignupCohort(0, 0, null));
        // Mockito returns empty lists for the other aggregate queries.
    }
}
