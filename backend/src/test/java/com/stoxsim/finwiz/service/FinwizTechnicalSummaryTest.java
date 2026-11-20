package com.stoxsim.finwiz.service;

import com.stoxsim.market.data.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinwizTechnicalSummaryTest {
    @Test void shortHistoriesDoNotMasqueradeAsFullPeriodIndicators() {
        var summary = FinwizContextService.technicalSummary(bars(10));
        assertThat(summary.latestClose()).isEqualByComparingTo("100");
        assertThat(summary.sma20()).isNull();
        assertThat(summary.sma50()).isNull();
        assertThat(summary.return20SessionsPercent()).isNull();
        assertThat(summary.high20()).isNull();
        assertThat(summary.rsi14()).isNull();
    }
    @Test void flatSeriesIsNeutralAndTwentySessionReturnNeedsTwentyOneCloses() {
        var twenty = FinwizContextService.technicalSummary(bars(20));
        assertThat(twenty.sma20()).isEqualByComparingTo("100");
        assertThat(twenty.return20SessionsPercent()).isNull();
        assertThat(twenty.rsi14()).isEqualByComparingTo("50");
        assertThat(FinwizContextService.technicalSummary(bars(21)).return20SessionsPercent()).isEqualByComparingTo("0");
        assertThat(FinwizContextService.technicalSummary(bars(50)).sma50()).isEqualByComparingTo("100");
    }
    private java.util.List<Candle> bars(int count) {
        return IntStream.range(0,count).mapToObj(i -> new Candle(Instant.EPOCH.plusSeconds(i*86400L),
            BigDecimal.valueOf(100),BigDecimal.valueOf(110),BigDecimal.valueOf(90),BigDecimal.valueOf(100),10L)).toList();
    }
}
