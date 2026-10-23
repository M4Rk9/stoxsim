package com.stoxsim.calendar.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import com.stoxsim.calendar.domain.MarketPhase;
import com.stoxsim.calendar.repository.MarketHolidayRepository;
import com.stoxsim.instrument.domain.MarketExchange;
import org.junit.jupiter.api.Test;

class VerifiedCalendarTest {
    private final MarketHolidayRepository holidays = mock(MarketHolidayRepository.class);
    private IndiaMarketSessionService service(String instant) {
        return new IndiaMarketSessionService(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), holidays);
    }
    @Test void officialClosuresAndEmergencyOverridesApplyToBothRegions() {
        var sessions = service("2026-10-20T06:00:00Z");
        assertThat(sessions.current(MarketExchange.NSE).phase()).isEqualTo(MarketPhase.HOLIDAY);
        assertThat(sessions.current(MarketExchange.NSE).orderDate()).isEqualTo(LocalDate.of(2026,10,21));
        assertThat(sessions.isTradingDay(MarketExchange.NSE,LocalDate.of(2026,1,15))).isFalse();
        assertThat(sessions.isTradingDay(MarketExchange.NYSE,LocalDate.of(2026,7,3))).isFalse();
        for (var exchange : new MarketExchange[]{MarketExchange.NASDAQ,MarketExchange.NYSE,MarketExchange.NYSE_ARCA,MarketExchange.AMEX,MarketExchange.CBOE})
            assertThat(sessions.isTradingDay(exchange,LocalDate.of(2026,11,26))).isFalse();
        var day=LocalDate.of(2026,10,21);
        when(holidays.existsByExchangeAndHolidayDate(MarketExchange.NASDAQ,day)).thenReturn(true);
        assertThat(sessions.isTradingDay(MarketExchange.NASDAQ,day)).isFalse();
        assertThat(sessions.isTradingDay(MarketExchange.NSE,day)).isTrue();
    }
    @Test void sundayBudgetAndMuhuratSessionsAreNotSkipped() {
        assertThat(service("2026-02-01T06:00:00Z").current(MarketExchange.NSE).executable()).isTrue();
        assertThat(service("2026-11-08T08:40:00Z").current(MarketExchange.NSE).phase()).isEqualTo(MarketPhase.PRE_OPEN_ORDER_ENTRY);
        assertThat(service("2026-11-08T08:46:00Z").current(MarketExchange.NSE).phase()).isEqualTo(MarketPhase.PRE_OPEN_MATCHING);
        assertThat(service("2026-11-08T08:50:00Z").current(MarketExchange.NSE).executable()).isTrue();
        var auction=service("2026-11-08T09:45:00Z").current(MarketExchange.NSE);
        assertThat(auction.phase()).isEqualTo(MarketPhase.CLOSING_AUCTION);
        assertThat(auction.executable()).isFalse();
        assertThat(auction.allowsOrderEntry()).isFalse();
        assertThat(service("2026-11-06T11:00:00Z").current(MarketExchange.NSE).orderDate()).isEqualTo(LocalDate.of(2026,11,8));
    }
    @Test void earlyCloseUsesNewYorkTimeAndQueuesAfterHoursForNextSession() {
        var day=LocalDate.of(2026,11,27);
        assertThat(service("2026-11-27T17:59:59Z").current(MarketExchange.NASDAQ).executable()).isTrue();
        var closed=service("2026-11-27T18:00:00Z").current(MarketExchange.NASDAQ);
        assertThat(closed.phase()).isEqualTo(MarketPhase.AFTER_HOURS);
        assertThat(closed.executable()).isFalse();
        assertThat(closed.orderDate()).isEqualTo(LocalDate.of(2026,11,30));
        assertThat(service("2026-11-27T17:59:59Z").hasSessionEnded(MarketExchange.NASDAQ,day)).isFalse();
        assertThat(service("2026-11-27T18:00:00Z").hasSessionEnded(MarketExchange.NASDAQ,day)).isTrue();
    }
    @Test void indiaCloseCannotExpireUsOrdersAndNextDayOrdersSurvive() {
        var sessions=service("2026-10-21T10:01:00Z");
        var day=LocalDate.of(2026,10,21);
        assertThat(sessions.hasSessionEnded(MarketExchange.NSE,day)).isTrue();
        assertThat(sessions.hasSessionEnded(MarketExchange.NYSE,day)).isFalse();
        assertThat(sessions.hasSessionEnded(MarketExchange.NSE,day.plusDays(1))).isFalse();
        assertThat(sessions.hasSessionEnded(MarketExchange.NYSE,day.minusDays(1))).isTrue();
    }
    @Test void daylightSavingChangesUtcOpenWithoutChangingExchangeHours() {
        assertThat(service("2026-03-06T14:29:59Z").current(MarketExchange.NASDAQ).executable()).isFalse();
        assertThat(service("2026-03-06T14:30:00Z").current(MarketExchange.NASDAQ).executable()).isTrue();
        assertThat(service("2026-03-09T13:30:00Z").current(MarketExchange.NASDAQ).executable()).isTrue();
    }
    @Test void uncoveredYearsAndYearEndQueueFailClosedWithoutThrowing() {
        var snapshot=service("2027-01-04T06:00:00Z").current(MarketExchange.NSE);
        assertThat(snapshot.phase()).isEqualTo(MarketPhase.CALENDAR_UNAVAILABLE);
        assertThat(snapshot.allowsOrderEntry()).isFalse();
        assertThat(snapshot.nextTransition()).isNull();
        assertThat(snapshot.orderDate()).isNull();
        assertThat(service("2026-12-31T11:00:00Z").current(MarketExchange.NSE).allowsOrderEntry()).isFalse();
    }

    @Test void published2027UsClosuresApplyWithoutExtendingIndiaCoverage() {
        var sessions = service("2027-01-04T15:00:00Z");
        for (var exchange : new MarketExchange[]{MarketExchange.NASDAQ, MarketExchange.NYSE, MarketExchange.NYSE_ARCA, MarketExchange.AMEX, MarketExchange.CBOE}) {
            for (var date : new String[]{"2027-01-01", "2027-01-18", "2027-02-15", "2027-03-26", "2027-05-31",
                    "2027-06-18", "2027-07-05", "2027-09-06", "2027-11-25", "2027-12-24"})
                assertThat(sessions.isTradingDay(exchange, LocalDate.parse(date))).as("%s %s", exchange, date).isFalse();
            assertThat(sessions.current(exchange).executable()).isTrue();
            // No Christmas Eve half-day or Friday New Year's observance in 2027.
            assertThat(service("2027-12-23T20:00:00Z").current(exchange).executable()).isTrue();
            assertThat(service("2027-12-31T20:00:00Z").current(exchange).executable()).isTrue();
            assertThat(service("2028-01-03T15:00:00Z").current(exchange).phase()).isEqualTo(MarketPhase.CALENDAR_UNAVAILABLE);
        }
        assertThat(sessions.current(MarketExchange.NSE).phase()).isEqualTo(MarketPhase.CALENDAR_UNAVAILABLE);
        assertThat(service("2026-12-31T22:00:00Z").current(MarketExchange.NYSE).orderDate()).isEqualTo(LocalDate.of(2027, 1, 4));
    }

    @Test void published2027EarlyCloseExpiresDayOrdersAndDstStillApplies() {
        var day = LocalDate.of(2027, 11, 26);
        assertThat(service("2027-11-26T17:59:59Z").current(MarketExchange.NYSE).executable()).isTrue();
        var afterClose = service("2027-11-26T18:00:00Z");
        assertThat(afterClose.current(MarketExchange.NYSE).executable()).isFalse();
        assertThat(afterClose.hasSessionEnded(MarketExchange.NYSE, day)).isTrue();
        assertThat(afterClose.current(MarketExchange.NYSE).orderDate()).isEqualTo(LocalDate.of(2027, 11, 29));
        assertThat(service("2027-03-12T14:30:00Z").current(MarketExchange.NYSE).executable()).isTrue();
        assertThat(service("2027-03-15T13:30:00Z").current(MarketExchange.NYSE).executable()).isTrue();
    }
}
