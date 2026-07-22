package com.stoxsim.watchlist.service;

import com.stoxsim.market.data.InstrumentKey;

public record WatchlistSubscriptionRemovedEvent(InstrumentKey instrument) {
}
