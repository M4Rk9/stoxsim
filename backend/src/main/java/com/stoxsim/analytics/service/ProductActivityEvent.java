package com.stoxsim.analytics.service;

import java.util.UUID;

/** Server-owned events are published only after a successful domain operation. */
public record ProductActivityEvent(UUID userId, Kind kind) {
    public enum Kind { ACTIVE, STOCK_OPENED, WATCHLIST_ADDED, ORDER_SUBMITTED, ORDER_EXECUTED }
}
