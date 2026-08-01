package com.stoxsim.market.api;

import java.time.Instant;
import java.util.List;

import com.stoxsim.market.data.MarketDataStatus;

public record MarketMoversResponse(
    String universe,
    Instant generatedAt,
    MarketDataStatus dataStatus,
    List<MarketMoverResponse> gainers,
    List<MarketMoverResponse> losers
) {
    public static MarketMoversResponse unavailable() {
        return unavailable("NIFTY_100");
    }

    public static MarketMoversResponse unavailable(String universe) {
        return new MarketMoversResponse(
            universe,
            null,
            MarketDataStatus.UNAVAILABLE,
            List.of(),
            List.of()
        );
    }
}
