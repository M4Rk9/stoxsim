package com.stoxsim.market.news;

import java.time.Instant;
import java.util.List;

public record NewsResponse(Status status, String provider, Instant checkedAt,
                           List<Article> articles, String message) {
    public enum Status { AVAILABLE, EMPTY, UNAVAILABLE, DISABLED }
    public record Article(String headline, String source, String url, Instant publishedAt) {}
}
