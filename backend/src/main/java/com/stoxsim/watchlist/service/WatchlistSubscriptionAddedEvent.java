package com.stoxsim.watchlist.service;

import com.stoxsim.market.data.InstrumentKey;

public record WatchlistSubscriptionAddedEvent(InstrumentKey instrument) {
}
