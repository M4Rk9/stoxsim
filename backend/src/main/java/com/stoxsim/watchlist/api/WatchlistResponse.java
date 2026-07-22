package com.stoxsim.watchlist.api;

import java.util.List;
import java.util.UUID;

public record WatchlistResponse(
    UUID id,
    String name,
    List<WatchlistItemResponse> items
) {
}
