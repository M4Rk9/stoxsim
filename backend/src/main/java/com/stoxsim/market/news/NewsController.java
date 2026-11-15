package com.stoxsim.market.news;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;

@RestController
@RequestMapping("/api/v1/instruments")
public class NewsController {
    private final NewsService news;
    public NewsController(NewsService news) { this.news = news; }
    @GetMapping("/{region}/{exchange}/{symbol}/news")
    public ResponseEntity<NewsResponse> get(@PathVariable MarketRegion region,
        @PathVariable MarketExchange exchange, @PathVariable String symbol) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(news.get(region, exchange, symbol));
    }
}
