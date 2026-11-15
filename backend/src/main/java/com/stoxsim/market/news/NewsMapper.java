package com.stoxsim.market.news;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Provider text is data, never HTML or an instruction to the AI. */
public final class NewsMapper {
    private NewsMapper() {}
    public static List<NewsResponse.Article> map(JsonNode items, boolean india, Instant now) {
        if (items == null || !items.isArray()) throw new IllegalArgumentException("Missing news list");
        var articles = new LinkedHashMap<String, NewsResponse.Article>();
        int examined = 0;
        for (JsonNode item : items) {
            if (++examined > 50) break;
            try {
                String title = item.path(india ? "heading" : "headline").asText("").strip();
                String link = item.path(india ? "article_link" : "url").asText("").strip();
                URI uri = URI.create(link);
                String host = uri.getHost();
                if (title.isBlank() || title.length() > 500 || link.length() > 2048
                    || !"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !host.contains(".") || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || host.endsWith(".local") || host.matches("[0-9.]+")) continue;
                Instant published = india
                    ? Instant.ofEpochMilli(item.path("published_time").asLong(0))
                    : Instant.parse(item.path("created_at").asText(""));
                if (published.isAfter(now) || published.isBefore(now.minus(Duration.ofDays(7)))) continue;
                // Destination domain is verifiable attribution; do not infer an author or publisher.
                articles.putIfAbsent(link, new NewsResponse.Article(title, host, link, published));
            } catch (IllegalArgumentException | java.time.DateTimeException ignored) {
                // Incomplete, unsafe or malformed articles do not become fabricated news.
            }
        }
        return articles.values().stream().sorted(Comparator.comparing(NewsResponse.Article::publishedAt).reversed())
            .limit(10).toList();
    }
}
