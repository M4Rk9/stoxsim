package com.stoxsim.market.data;

import com.stoxsim.market.domain.MarketRegion;

public record InstrumentKey(
    String provider,
    String value,
    MarketRegion marketRegion
) {
}
