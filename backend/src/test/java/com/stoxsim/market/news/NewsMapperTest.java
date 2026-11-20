package com.stoxsim.market.news;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class NewsMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Instant now = Instant.parse("2026-11-15T12:00:00Z");
    @Test void excludesUnsafeUndatedFutureAndOldArticlesAndDeduplicates() {
        var rows = json.readTree("""
            [{"headline":"Valid sample","url":"https://example.com/story","created_at":"2026-11-14T12:00:00Z"},
             {"headline":"Duplicate","url":"https://example.com/story","created_at":"2026-11-14T12:00:00Z"},
             {"headline":"Script","url":"javascript:alert(1)","created_at":"2026-11-14T12:00:00Z"},
             {"headline":"Credentials","url":"https://user:secret@example.com/story","created_at":"2026-11-14T12:00:00Z"},
             {"headline":"Old","url":"https://example.com/old","created_at":"2026-10-14T12:00:00Z"},
             {"headline":"Future","url":"https://example.com/future","created_at":"2026-11-16T12:00:00Z"},
             {"headline":"Undated","url":"https://example.com/undated"}]
            """);
        var result = NewsMapper.map(rows, false, now);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().source()).isEqualTo("example.com");
        assertThat(result.getFirst().headline()).isEqualTo("Valid sample");
    }
    @Test void indiaUsesMillisecondPublicationTimeAndRejectsMissingTime() {
        var rows = json.readTree("[{\"heading\":\"Sample\",\"article_link\":\"https://upstox.com/news/sample\",\"published_time\":"
            + now.minusSeconds(60).toEpochMilli() + "},{\"heading\":\"Missing time\",\"article_link\":\"https://upstox.com/news/other\"}]");
        assertThat(NewsMapper.map(rows, true, now)).hasSize(1);
        assertThat(NewsMapper.map(rows, true, now).getFirst().publishedAt()).isEqualTo(now.minusSeconds(60));
    }
    @Test void missingProviderPayloadIsNotReportedAsNoNews() {
        assertThatThrownBy(() -> NewsMapper.map(json.readTree("{}"), false, now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(NewsMapper.map(json.readTree("[]"), false, now)).isEmpty();
    }
}
