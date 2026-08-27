package com.stoxsim.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountMailService;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioAllocationResponse;
import com.stoxsim.portfolio.api.PortfolioAttributionResponse;
import com.stoxsim.portfolio.api.PortfolioInsightsResponse;
import com.stoxsim.portfolio.service.PortfolioInsightsService;
import com.stoxsim.report.api.WeeklyMarketReportResponse;
import com.stoxsim.report.api.WeeklyPortfolioReportResponse;
import com.stoxsim.report.api.WeeklyReportPreferenceResponse;
import com.stoxsim.report.api.WeeklyReportSnapshotResponse;
import com.stoxsim.report.domain.WeeklyPortfolioReport;
import com.stoxsim.report.domain.WeeklyReportDeliveryStatus;
import com.stoxsim.report.domain.WeeklyReportPreference;
import com.stoxsim.report.repository.WeeklyPortfolioReportRepository;
import com.stoxsim.report.repository.WeeklyReportPreferenceRepository;
import com.stoxsim.trade.repository.TradeRepository;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

@Service
public class WeeklyReportService {

    public static final String SNAPSHOT_VERSION = "weekly-portfolio-report-v1";
    public static final String DISCLAIMER = "This weekly report explains past simulated portfolio activity for education. It is not investment advice and does not predict future returns.";
    public static final String SCHEDULE = "Mondays after 08:00 in your selected timezone";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AppUserRepository users;
    private final WeeklyReportPreferenceRepository preferences;
    private final WeeklyPortfolioReportRepository reports;
    private final PortfolioInsightsService insights;
    private final TradeRepository trades;
    private final AccountProperties accountProperties;
    private final AccountMailService mailService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final String frontendUrl;

    public WeeklyReportService(
        AppUserRepository users,
        WeeklyReportPreferenceRepository preferences,
        WeeklyPortfolioReportRepository reports,
        PortfolioInsightsService insights,
        TradeRepository trades,
        AccountProperties accountProperties,
        AccountMailService mailService,
        ObjectMapper objectMapper,
        Clock clock,
        MeterRegistry meterRegistry,
        @Value("${stoxsim.frontend-url}") String frontendUrl
    ) {
        this.users = users;
        this.preferences = preferences;
        this.reports = reports;
        this.insights = insights;
        this.trades = trades;
        this.accountProperties = accountProperties;
        this.mailService = mailService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.frontendUrl = frontendUrl;
    }

    @Transactional(readOnly = true)
    public WeeklyReportPreferenceResponse preference(UUID userId) {
        return preferences.findByUserId(userId)
            .map(this::response)
            .orElse(new WeeklyReportPreferenceResponse(
                false,
                WeeklyReportPreference.DEFAULT_ZONE_ID,
                SCHEDULE,
                true,
                null
            ));
    }

    @Transactional
    public WeeklyReportPreferenceResponse updatePreference(
        UUID userId,
        boolean enabled,
        String requestedZoneId
    ) {
        String zoneId = validZoneId(requestedZoneId);
        AppUser user = requireUser(userId);
        if (enabled && !user.isEmailVerified()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Verify your email before enabling weekly reports"
            );
        }
        WeeklyReportPreference preference = preferences.findByUserId(userId)
            .orElseGet(() -> new WeeklyReportPreference(user));
        preference.update(enabled, zoneId);
        WeeklyReportPreference saved = preferences.save(preference);
        meterRegistry.counter(
            "stoxsim.weekly_reports.preference",
            "enabled",
            String.valueOf(enabled)
        ).increment();
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<WeeklyPortfolioReportResponse> reports(UUID userId) {
        requireUser(userId);
        return reports.findAllByUserIdOrderByPeriodEndDesc(userId, PageRequest.of(0, 12))
            .stream()
            .map(this::response)
            .toList();
    }

    public WeeklyReportSnapshotResponse preview(UUID userId, String requestedZoneId) {
        ZoneId zoneId = ZoneId.of(validZoneId(requestedZoneId));
        LocalDate periodEnd = LocalDate.now(clock.withZone(zoneId));
        return snapshot(userId, periodEnd.minusDays(6), periodEnd, zoneId);
    }

    public void generateAndDeliver(UUID userId, LocalDate periodEnd, ZoneId zoneId) {
        WeeklyReportPreference preference = preferences.findByUserId(userId).orElse(null);
        AppUser user = users.findById(userId).orElse(null);
        if (preference == null || !preference.isEnabled() || user == null || !user.isEmailVerified()) {
            return;
        }

        WeeklyPortfolioReport report = reports.findByUserIdAndPeriodEnd(userId, periodEnd)
            .orElseGet(() -> createReport(userId, user, periodEnd, zoneId));
        if (report.getDeliveryStatus() == WeeklyReportDeliveryStatus.SENT
            || report.getDeliveryAttempts() >= 3) {
            return;
        }

        WeeklyReportSnapshotResponse snapshot = deserialize(report.getSnapshotJson());
        boolean delivered = mailService.sendWeeklyPortfolioReport(
            user,
            "Your StoxSim weekly portfolio report",
            emailBody(user, snapshot)
        );
        report.recordDelivery(delivered, clock.instant());
        reports.save(report);
        meterRegistry.counter(
            "stoxsim.weekly_reports.delivery",
            "result",
            delivered ? "sent" : "failed"
        ).increment();
    }

    private WeeklyPortfolioReport createReport(
        UUID userId,
        AppUser user,
        LocalDate periodEnd,
        ZoneId zoneId
    ) {
        LocalDate periodStart = periodEnd.minusDays(6);
        WeeklyReportSnapshotResponse snapshot = snapshot(
            userId,
            periodStart,
            periodEnd,
            zoneId
        );
        WeeklyPortfolioReport report = reports.save(new WeeklyPortfolioReport(
            user,
            periodStart,
            periodEnd,
            serialize(snapshot),
            clock.instant()
        ));
        meterRegistry.counter("stoxsim.weekly_reports.generated").increment();
        return report;
    }

    private WeeklyReportSnapshotResponse snapshot(
        UUID userId,
        LocalDate periodStart,
        LocalDate periodEnd,
        ZoneId zoneId
    ) {
        WeeklyReportSnapshotResponse previous = reports
            .findFirstByUserIdAndPeriodEndBeforeOrderByPeriodEndDesc(userId, periodEnd)
            .map(report -> deserialize(report.getSnapshotJson()))
            .orElse(null);
        Instant from = periodStart.atStartOfDay(zoneId).toInstant();
        Instant until = periodEnd.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<WeeklyMarketReportResponse> markets = List.of(
            market(userId, MarketRegion.INDIA, previous, from, until),
            market(userId, MarketRegion.UNITED_STATES, previous, from, until)
        );
        long tradeCount = markets.stream()
            .mapToLong(WeeklyMarketReportResponse::tradesExecuted)
            .sum();
        List<String> notes = new ArrayList<>();
        notes.add(tradeCount == 0
            ? "No paper trades were executed during this report period."
            : tradeCount + " paper " + (tradeCount == 1 ? "trade was" : "trades were")
                + " executed across both simulated markets.");
        if (markets.stream().anyMatch(market -> "LOW".equals(market.confidence()))) {
            notes.add("At least one market has limited pricing coverage; review its data labels before interpreting changes.");
        }
        notes.add("Compare allocation and contribution changes with the learning goal you set before trading.");
        return new WeeklyReportSnapshotResponse(
            SNAPSHOT_VERSION,
            periodStart,
            periodEnd,
            markets,
            List.copyOf(notes),
            DISCLAIMER,
            clock.instant()
        );
    }

    private WeeklyMarketReportResponse market(
        UUID userId,
        MarketRegion marketRegion,
        WeeklyReportSnapshotResponse previous,
        Instant from,
        Instant until
    ) {
        PortfolioInsightsResponse current = insights.analyze(userId, marketRegion);
        BigDecimal startingCapital = marketRegion == MarketRegion.INDIA
            ? accountProperties.getIndiaStartingBalance()
            : accountProperties.getUnitedStatesStartingBalance();
        BigDecimal accountValue = money(startingCapital.add(current.totalProfitLoss()));
        WeeklyMarketReportResponse prior = previous == null
            ? null
            : previous.markets().stream()
                .filter(market -> market.marketRegion() == marketRegion)
                .findFirst()
                .orElse(null);
        PortfolioAllocationResponse largestAllocation = current.allocations().stream()
            .findFirst()
            .orElse(null);
        PortfolioAttributionResponse largestContribution = current.attributions().stream()
            .findFirst()
            .orElse(null);

        return new WeeklyMarketReportResponse(
            marketRegion,
            current.currency(),
            accountValue,
            prior == null ? null : money(accountValue.subtract(prior.accountValue())),
            money(current.totalProfitLoss()),
            prior == null ? null : money(current.totalProfitLoss().subtract(prior.totalProfitLoss())),
            percent(current.totalProfitLoss(), startingCapital),
            money(current.realizedProfitLoss()),
            money(current.unrealizedProfitLoss()),
            percentage(current.cashWeightPercent()),
            percentage(current.investedWeightPercent()),
            trades.countForReport(userId, marketRegion, from, until),
            largestAllocation == null ? null : largestAllocation.symbol(),
            largestAllocation == null ? null : percentage(largestAllocation.investedWeightPercent()),
            largestContribution == null ? null : largestContribution.symbol(),
            largestContribution == null ? null : money(largestContribution.totalContribution()),
            current.status(),
            current.confidence(),
            percentage(current.dataCoveragePercent())
        );
    }

    private String emailBody(AppUser user, WeeklyReportSnapshotResponse snapshot) {
        StringBuilder body = new StringBuilder()
            .append("Hello ").append(user.getDisplayName()).append(",\n\n")
            .append("Your StoxSim learning report for ")
            .append(snapshot.periodStart()).append(" to ").append(snapshot.periodEnd())
            .append(" is ready.\n\n");
        for (WeeklyMarketReportResponse market : snapshot.markets()) {
            body.append(market.marketRegion() == MarketRegion.INDIA ? "India" : "United States")
                .append(" simulated portfolio\n")
                .append("Account value: ").append(market.currency()).append(" ")
                .append(market.accountValue().toPlainString()).append("\n")
                .append("Total simulated P/L: ").append(market.currency()).append(" ")
                .append(market.totalProfitLoss().toPlainString()).append("\n")
                .append("Paper trades this week: ").append(market.tradesExecuted()).append("\n\n");
        }
        body.append("Open StoxSim to review allocation and contribution details:\n")
            .append(frontendUrl).append("/portfolio\n\n")
            .append(snapshot.disclaimer());
        return body.toString();
    }

    private WeeklyReportPreferenceResponse response(WeeklyReportPreference preference) {
        return new WeeklyReportPreferenceResponse(
            preference.isEnabled(),
            preference.getZoneId(),
            SCHEDULE,
            true,
            preference.getUpdatedAt()
        );
    }

    private WeeklyPortfolioReportResponse response(WeeklyPortfolioReport report) {
        return new WeeklyPortfolioReportResponse(
            report.getId(),
            report.getDeliveryStatus(),
            report.getDeliveryAttempts(),
            report.getDeliveryAttemptedAt(),
            deserialize(report.getSnapshotJson())
        );
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String validZoneId(String requestedZoneId) {
        try {
            return ZoneId.of(requestedZoneId.trim()).getId();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timezone is not valid");
        }
    }

    private String serialize(WeeklyReportSnapshotResponse snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Weekly report snapshot could not be serialized", exception);
        }
    }

    private WeeklyReportSnapshotResponse deserialize(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, WeeklyReportSnapshotResponse.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Weekly report snapshot could not be read", exception);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
    }
}
