package com.stoxsim.market.provider.upstox;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.stoxsim.market.data.Candle;

public final class UpstoxCandleMapper {

    private UpstoxCandleMapper() {
    }

    public static List<Candle> map(List<List<Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
            .filter(row -> row != null && row.size() >= 6)
            .map(UpstoxCandleMapper::mapRow)
            .toList();
    }

    private static Candle mapRow(List<Object> row) {
        return new Candle(
            OffsetDateTime.parse(row.get(0).toString()).toInstant(),
            decimal(row.get(1)),
            decimal(row.get(2)),
            decimal(row.get(3)),
            decimal(row.get(4)),
            Long.valueOf(row.get(5).toString())
        );
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }
}
