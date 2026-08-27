package com.stoxsim.report.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stoxsim.report.domain.WeeklyReportPreference;
import com.stoxsim.report.repository.WeeklyReportPreferenceRepository;

@Component
public class WeeklyReportScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeeklyReportScheduler.class);
    private static final int PAGE_SIZE = 100;

    private final WeeklyReportPreferenceRepository preferences;
    private final WeeklyReportService reports;
    private final Clock clock;

    public WeeklyReportScheduler(
        WeeklyReportPreferenceRepository preferences,
        WeeklyReportService reports,
        Clock clock
    ) {
        this.preferences = preferences;
        this.reports = reports;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    public void deliverDueReports() {
        int pageNumber = 0;
        Page<WeeklyReportPreference> page;
        do {
            page = preferences.findAllByEnabledTrue(PageRequest.of(pageNumber, PAGE_SIZE));
            for (WeeklyReportPreference preference : page) {
                deliverIfDue(preference);
            }
            pageNumber++;
        } while (page.hasNext());
    }

    void deliverIfDue(WeeklyReportPreference preference) {
        try {
            ZonedDateTime local = clock.instant().atZone(
                java.time.ZoneId.of(preference.getZoneId())
            );
            if (local.getDayOfWeek() != DayOfWeek.MONDAY || local.getHour() < 8) {
                return;
            }
            LocalDate periodEnd = local.toLocalDate().minusDays(1);
            reports.generateAndDeliver(preference.getUserId(), periodEnd, local.getZone());
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Weekly portfolio report failed for user {}",
                preference.getUserId(),
                exception
            );
        }
    }
}
