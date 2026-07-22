package com.stoxsim.market.provider.upstox;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stoxsim.market.data.CandleInterval;

class UpstoxCandleMapperTest {

    @Test
    void mapsSdkCandleRowsWithoutUsingFloatingPointMath() {
        var candles = UpstoxCandleMapper.map(List.of(List.of(
            "2026-07-22T09:15:00+05:30",
            "1412.10",
            "1420.25",
            "1408.50",
            "1418.75",
            "123456",
            "0"
        )));

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().timestamp())
            .isEqualTo(Instant.parse("2026-07-22T03:45:00Z"));
        assertThat(candles.getFirst().close()).isEqualByComparingTo(new BigDecimal("1418.75"));
        assertThat(candles.getFirst().volume()).isEqualTo(123456L);
    }

    @Test
    void mapsSupportedIntervalsToUpstoxUnits() {
        assertThat(UpstoxCandleInterval.from(CandleInterval.FIFTEEN_MINUTES))
            .isEqualTo(new UpstoxCandleInterval("minutes", 15));
        assertThat(UpstoxCandleInterval.from(CandleInterval.ONE_WEEK))
            .isEqualTo(new UpstoxCandleInterval("weeks", 1));
    }
}
