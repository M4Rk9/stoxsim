package com.stoxsim.watchlist.api;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddWatchlistItemRequest(
    @NotNull MarketRegion marketRegion,
    @NotNull MarketExchange exchange,
    @NotBlank String symbol
) {
}
