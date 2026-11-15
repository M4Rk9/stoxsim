package com.stoxsim.market.news;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.upstox.UpstoxNewsClient;
import com.stoxsim.market.provider.alpaca.AlpacaRestClient;

@Service
public class NewsService {
    private final TradableInstrumentRepository instruments;
    private final NewsProperties settings;
    private final UpstoxNewsClient upstox;
    private final AlpacaRestClient alpaca;
    public NewsService(TradableInstrumentRepository instruments, NewsProperties settings,
                       UpstoxNewsClient upstox, AlpacaRestClient alpaca) {
        this.instruments = instruments; this.settings = settings; this.upstox = upstox; this.alpaca = alpaca;
    }
    public NewsResponse get(MarketRegion region, MarketExchange exchange, String symbol) {
        if (symbol.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid listing");
        var instrument = instruments.findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(region, exchange, symbol)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        boolean india = region == MarketRegion.INDIA;
        String provider = india ? "Upstox" : "Alpaca";
        Instant now = Instant.now();
        if (!(india ? settings.isUpstoxEnabled() : settings.isAlpacaEnabled()))
            return new NewsResponse(NewsResponse.Status.DISABLED, provider, now, List.of(),
                "News is not available for this market yet. Check company announcements and original reporting.");
        if (instrument.getInstrumentType() != InstrumentType.EQUITY && instrument.getInstrumentType() != InstrumentType.ETF)
            return new NewsResponse(NewsResponse.Status.UNAVAILABLE, provider, now, List.of(), "News is unavailable for this listing.");
        try {
            var root = india ? upstox.get(instrument.getInstrumentKey())
                : alpaca.getNews(instrument.getTradingSymbol(), now.minus(Duration.ofDays(7)));
            var items = root == null ? null : india ? root.path("data").get(instrument.getInstrumentKey()) : root.get("news");
            var articles = NewsMapper.map(items, india, now);
            return new NewsResponse(articles.isEmpty() ? NewsResponse.Status.EMPTY : NewsResponse.Status.AVAILABLE,
                provider, now, articles, "Selected headlines from the past seven days. Coverage may be incomplete or delayed; read the original source.");
        } catch (RuntimeException exception) {
            // Never return provider errors, URLs with tokens, or credential-bearing exception text.
            return new NewsResponse(NewsResponse.Status.UNAVAILABLE, provider, now, List.of(),
                "News could not be checked just now. Try again later or consult original sources.");
        }
    }
}
