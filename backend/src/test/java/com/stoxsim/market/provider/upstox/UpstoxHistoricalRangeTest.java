package com.stoxsim.market.provider.upstox;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.stoxsim.market.data.Candle;
import tools.jackson.databind.ObjectMapper;
class UpstoxHistoricalRangeTest {
    private final LocalDate today = LocalDate.of(2026, 11, 6);
    private final String key = "BSE_EQ|INE002A01018";
    // UTC is still Nov 5; the split must follow the exchange date.
    private final UpstoxHistoricalCandleClient client = spy(new UpstoxHistoricalCandleClient(
        new ObjectMapper(), mock(UpstoxClientFactory.class),
        Clock.fixed(Instant.parse("2026-11-05T23:00:00Z"), ZoneOffset.UTC)));
    private Candle candle(String time, int close) {
        return new Candle(Instant.parse(time), BigDecimal.valueOf(close), BigDecimal.valueOf(close),
            BigDecimal.valueOf(close), BigDecimal.valueOf(close), 100L);
    }
    @Test void combinesHistoricalAndCurrentSessionInTimeOrderWithoutDuplicates() {
        var old = candle("2026-11-05T04:00:00Z", 100);
        var current = candle("2026-11-06T04:00:00Z", 101);
        doReturn(List.of(old, old)).when(client).getCandles(key, "minutes", 1, today.minusDays(7), today.minusDays(1));
        doReturn(List.of(current, old)).when(client).getIntradayCandles(key, "minutes", 1);
        assertThat(client.getRange(key, "minutes", 1, today.minusDays(7), today)).containsExactly(old, current);
    }
    @Test void todayOnlyDoesNotRequestAnInvalidHistoricalRange() {
        var current = candle("2026-11-06T04:00:00Z", 101);
        doReturn(List.of(current)).when(client).getIntradayCandles(key, "days", 1);
        assertThat(client.getRange(key, "days", 1, today, today)).containsExactly(current);
        verify(client, never()).getCandles(anyString(), anyString(), anyInt(), any(), any());
    }
    @Test void emptyCurrentSessionKeepsPriorAvailableSession() {
        var old = candle("2026-11-05T04:00:00Z", 100);
        doReturn(List.of(old)).when(client).getCandles(key, "minutes", 15, today.minusDays(7), today.minusDays(1));
        doReturn(List.of()).when(client).getIntradayCandles(key, "minutes", 15);
        assertThat(client.getRange(key, "minutes", 15, today.minusDays(7), today)).containsExactly(old);
    }
    @Test void historicalOnlyDoesNotFetchIntraday() {
        doReturn(List.of()).when(client).getCandles(key, "days", 1, today.minusDays(7), today.minusDays(1));
        client.getRange(key, "days", 1, today.minusDays(7), today.minusDays(1));
        verify(client, never()).getIntradayCandles(anyString(), anyString(), anyInt());
    }
    @Test void weeklySeriesDoesNotMixDailyOrIntradayBars() {
        doReturn(List.of()).when(client).getCandles(key, "weeks", 1, today.minusYears(5), today);
        client.getRange(key, "weeks", 1, today.minusYears(5), today);
        verify(client, never()).getIntradayCandles(anyString(), anyString(), anyInt());
    }
    @Test void currentSessionFailureDoesNotMasqueradeAsFreshHistory() {
        doThrow(new MarketDataUnavailableException("Unavailable")).when(client).getIntradayCandles(key, "minutes", 1);
        assertThatThrownBy(() -> client.getRange(key, "minutes", 1, today, today)).isInstanceOf(MarketDataUnavailableException.class);
    }
}
