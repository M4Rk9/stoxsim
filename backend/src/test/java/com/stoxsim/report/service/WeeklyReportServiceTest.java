package com.stoxsim.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountMailService;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioAllocationResponse;
import com.stoxsim.portfolio.api.PortfolioAttributionResponse;
import com.stoxsim.portfolio.api.PortfolioInsightsResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.service.PortfolioInsightsService;
import com.stoxsim.report.domain.WeeklyPortfolioReport;
import com.stoxsim.report.domain.WeeklyReportDeliveryStatus;
import com.stoxsim.report.domain.WeeklyReportPreference;
import com.stoxsim.report.repository.WeeklyPortfolioReportRepository;
import com.stoxsim.report.repository.WeeklyReportPreferenceRepository;
import com.stoxsim.trade.repository.TradeRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-31T03:15:00Z");

    @Mock private AppUserRepository users;
    @Mock private WeeklyReportPreferenceRepository preferences;
    @Mock private WeeklyPortfolioReportRepository reports;
    @Mock private PortfolioInsightsService insights;
    @Mock private TradeRepository trades;
    @Mock private AccountProperties accountProperties;
    @Mock private AccountMailService mailService;

    private ObjectMapper objectMapper;
    private Clock clock;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void requiresAVerifiedEmailBeforeOptIn() {
        AppUser user = new AppUser("learner@example.com", "hash", "Learner");
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().updatePreference(
            USER_ID,
            true,
            "Asia/Kolkata"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Verify your email");

        verify(preferences, never()).save(any());
    }

    @Test
    void storesAnExplicitOptInAndValidTimezone() {
        AppUser user = new AppUser("learner@example.com", "hash", "Learner");
        user.markEmailVerified();
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(preferences.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(preferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().updatePreference(USER_ID, true, "Asia/Kolkata");

        assertThat(response.enabled()).isTrue();
        assertThat(response.zoneId()).isEqualTo("Asia/Kolkata");
        assertThat(response.verifiedEmailRequired()).isTrue();
        assertThat(meterRegistry.counter(
            "stoxsim.weekly_reports.preference",
            "enabled",
            "true"
        ).count()).isEqualTo(1);
    }

    @Test
    void buildsAMarketIsolatedPreviewWithTradeCounts() {
        when(reports.findFirstByUserIdAndPeriodEndBeforeOrderByPeriodEndDesc(
            USER_ID,
            LocalDate.of(2026, 8, 31)
        )).thenReturn(Optional.empty());
        when(insights.analyze(USER_ID, MarketRegion.INDIA))
            .thenReturn(insights(MarketRegion.INDIA, "INR", "1250", "75"));
        when(insights.analyze(USER_ID, MarketRegion.UNITED_STATES))
            .thenReturn(insights(MarketRegion.UNITED_STATES, "USD", "-40", "20"));
        when(accountProperties.getIndiaStartingBalance()).thenReturn(new BigDecimal("500000"));
        when(accountProperties.getUnitedStatesStartingBalance()).thenReturn(new BigDecimal("10000"));
        when(trades.countForReport(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(2L, 1L);

        var preview = service().preview(USER_ID, "Asia/Kolkata");

        assertThat(preview.version()).isEqualTo("weekly-portfolio-report-v1");
        assertThat(preview.periodStart()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(preview.periodEnd()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(preview.markets()).extracting(market -> market.marketRegion())
            .containsExactly(MarketRegion.INDIA, MarketRegion.UNITED_STATES);
        assertThat(preview.markets().getFirst().accountValue())
            .isEqualByComparingTo("501250.0000");
        assertThat(preview.markets().getFirst().tradesExecuted()).isEqualTo(2);
        assertThat(preview.learningNotes()).anyMatch(note -> note.contains("3 paper trades"));
    }

    @Test
    void recordsSuccessfulDeliveryAndDoesNotResendASentPeriod() throws Exception {
        AppUser user = new AppUser("learner@example.com", "hash", "Learner");
        user.markEmailVerified();
        WeeklyReportPreference preference = org.mockito.Mockito.mock(WeeklyReportPreference.class);
        when(preference.isEnabled()).thenReturn(true);
        when(preferences.findByUserId(USER_ID)).thenReturn(Optional.of(preference));
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(reports.findByUserIdAndPeriodEnd(USER_ID, LocalDate.of(2026, 8, 30)))
            .thenReturn(Optional.empty());
        when(reports.findFirstByUserIdAndPeriodEndBeforeOrderByPeriodEndDesc(
            USER_ID,
            LocalDate.of(2026, 8, 30)
        )).thenReturn(Optional.empty());
        when(insights.analyze(USER_ID, MarketRegion.INDIA))
            .thenReturn(insights(MarketRegion.INDIA, "INR", "0", "0"));
        when(insights.analyze(USER_ID, MarketRegion.UNITED_STATES))
            .thenReturn(insights(MarketRegion.UNITED_STATES, "USD", "0", "0"));
        when(accountProperties.getIndiaStartingBalance()).thenReturn(new BigDecimal("500000"));
        when(accountProperties.getUnitedStatesStartingBalance()).thenReturn(new BigDecimal("10000"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mailService.sendWeeklyPortfolioReport(any(), any(), any())).thenReturn(true);

        service().generateAndDeliver(
            USER_ID,
            LocalDate.of(2026, 8, 30),
            ZoneId.of("Asia/Kolkata")
        );

        var reportCaptor = org.mockito.ArgumentCaptor.forClass(WeeklyPortfolioReport.class);
        verify(reports, org.mockito.Mockito.atLeastOnce()).save(reportCaptor.capture());
        WeeklyPortfolioReport delivered = reportCaptor.getValue();
        assertThat(delivered.getDeliveryStatus()).isEqualTo(WeeklyReportDeliveryStatus.SENT);
        assertThat(delivered.getDeliveryAttemptedAt()).isEqualTo(NOW);
        verify(mailService).sendWeeklyPortfolioReport(
            any(),
            org.mockito.ArgumentMatchers.contains("weekly portfolio report"),
            org.mockito.ArgumentMatchers.contains("/portfolio")
        );

        when(reports.findByUserIdAndPeriodEnd(USER_ID, LocalDate.of(2026, 8, 30)))
            .thenReturn(Optional.of(delivered));
        service().generateAndDeliver(
            USER_ID,
            LocalDate.of(2026, 8, 30),
            ZoneId.of("Asia/Kolkata")
        );
        verify(mailService, org.mockito.Mockito.times(1))
            .sendWeeklyPortfolioReport(any(), any(), any());
    }

    private WeeklyReportService service() {
        return new WeeklyReportService(
            users,
            preferences,
            reports,
            insights,
            trades,
            accountProperties,
            mailService,
            objectMapper,
            clock,
            meterRegistry,
            "https://stoxsim.com"
        );
    }

    private PortfolioInsightsResponse insights(
        MarketRegion marketRegion,
        String currency,
        String totalProfitLoss,
        String largestContribution
    ) {
        BigDecimal total = new BigDecimal(totalProfitLoss);
        return new PortfolioInsightsResponse(
            marketRegion,
            currency,
            "portfolio-insights-v1",
            "AVAILABLE",
            "HIGH",
            new BigDecimal("100"),
            new BigDecimal("400000"),
            new BigDecimal("80"),
            new BigDecimal("20"),
            new BigDecimal("250"),
            total.subtract(new BigDecimal("250")),
            total,
            List.of(new PortfolioAllocationResponse(
                marketRegion == MarketRegion.INDIA ? "NSE" : "NASDAQ",
                "LEARN",
                "Learning Corp",
                new BigDecimal("100000"),
                new BigDecimal("100"),
                new BigDecimal("20"),
                total,
                BigDecimal.ZERO,
                PricingStatus.CLOSED
            )),
            List.of(new PortfolioAttributionResponse(
                marketRegion == MarketRegion.INDIA ? "NSE" : "NASDAQ",
                "LEARN",
                "Learning Corp",
                new BigDecimal("250"),
                total.subtract(new BigDecimal("250")),
                new BigDecimal(largestContribution),
                BigDecimal.ZERO,
                "GAIN"
            )),
            List.of(),
            "Educational only",
            NOW
        );
    }
}
