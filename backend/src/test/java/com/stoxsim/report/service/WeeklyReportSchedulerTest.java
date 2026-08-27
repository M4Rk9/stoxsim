package com.stoxsim.report.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.report.domain.WeeklyReportPreference;
import com.stoxsim.report.repository.WeeklyReportPreferenceRepository;

@ExtendWith(MockitoExtension.class)
class WeeklyReportSchedulerTest {

    @Mock private WeeklyReportPreferenceRepository preferences;
    @Mock private WeeklyReportService reports;
    @Mock private WeeklyReportPreference preference;

    @Test
    void deliversAfterEightOnMondayInTheSelectedTimezone() {
        UUID userId = UUID.randomUUID();
        when(preference.getUserId()).thenReturn(userId);
        when(preference.getZoneId()).thenReturn("Asia/Kolkata");
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T03:15:00Z"), ZoneOffset.UTC);

        scheduler(clock).deliverIfDue(preference);

        verify(reports).generateAndDeliver(
            userId,
            LocalDate.of(2026, 8, 30),
            ZoneId.of("Asia/Kolkata")
        );
    }

    @Test
    void doesNotDeliverBeforeTheMondayWindow() {
        when(preference.getZoneId()).thenReturn("Asia/Kolkata");
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T01:15:00Z"), ZoneOffset.UTC);

        scheduler(clock).deliverIfDue(preference);

        verify(reports, never()).generateAndDeliver(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private WeeklyReportScheduler scheduler(Clock clock) {
        return new WeeklyReportScheduler(preferences, reports, clock);
    }
}
