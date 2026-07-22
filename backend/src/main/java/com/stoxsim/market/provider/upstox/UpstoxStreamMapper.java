package com.stoxsim.market.provider.upstox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.upstox.feeder.MarketUpdateV3;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;

@Component
public class UpstoxStreamMapper {

    public List<Quote> map(MarketUpdateV3 update) {
        if (update == null || update.getFeeds() == null) {
            return List.of();
        }
        Instant receivedAt = Instant.now();
        List<Quote> quotes = new ArrayList<>(update.getFeeds().size());
        update.getFeeds().forEach((key, feed) -> {
            Quote quote = mapFeed(key, feed, update.getCurrentTs(), receivedAt);
            if (quote != null) {
                quotes.add(quote);
            }
        });
        return List.copyOf(quotes);
    }

    private Quote mapFeed(
        String key,
        MarketUpdateV3.Feed feed,
        long updateTimestamp,
        Instant receivedAt
    ) {
        if (feed == null) {
            return null;
        }

        MarketUpdateV3.LTPC ltpc = feed.getLtpc();
        MarketUpdateV3.MarketFullFeed marketFeed = null;
        MarketUpdateV3.IndexFullFeed indexFeed = null;
        MarketUpdateV3.Quote depth = null;
        MarketUpdateV3.MarketOHLC marketOhlc = null;
        Long volume = null;

        if (feed.getFullFeed() != null) {
            marketFeed = feed.getFullFeed().getMarketFF();
            indexFeed = feed.getFullFeed().getIndexFF();
            if (marketFeed != null) {
                ltpc = marketFeed.getLtpc();
                marketOhlc = marketFeed.getMarketOHLC();
                volume = marketFeed.getVtt();
                if (
                    marketFeed.getMarketLevel() != null
                    && marketFeed.getMarketLevel().getBidAskQuote() != null
                    && !marketFeed.getMarketLevel().getBidAskQuote().isEmpty()
                ) {
                    depth = marketFeed.getMarketLevel().getBidAskQuote().getFirst();
                }
            } else if (indexFeed != null) {
                ltpc = indexFeed.getLtpc();
                marketOhlc = indexFeed.getMarketOHLC();
            }
        }

        if (feed.getFirstLevelWithGreeks() != null) {
            ltpc = feed.getFirstLevelWithGreeks().getLtpc();
            depth = feed.getFirstLevelWithGreeks().getFirstDepth();
            volume = feed.getFirstLevelWithGreeks().getVtt();
        }

        if (ltpc == null || ltpc.getLtp() <= 0) {
            return null;
        }

        MarketUpdateV3.OHLC daily = dailyOhlc(marketOhlc);
        long exchangeMillis = ltpc.getLtt() > 0 ? ltpc.getLtt() : updateTimestamp;

        return new Quote(
            new InstrumentKey("UPSTOX", key, MarketRegion.INDIA),
            BigDecimal.valueOf(ltpc.getLtp()),
            depth == null || depth.getBidP() <= 0 ? null : BigDecimal.valueOf(depth.getBidP()),
            depth == null || depth.getAskP() <= 0 ? null : BigDecimal.valueOf(depth.getAskP()),
            daily == null ? null : BigDecimal.valueOf(daily.getOpen()),
            daily == null ? null : BigDecimal.valueOf(daily.getHigh()),
            daily == null ? null : BigDecimal.valueOf(daily.getLow()),
            daily == null ? null : BigDecimal.valueOf(daily.getClose()),
            ltpc.getCp() <= 0 ? null : BigDecimal.valueOf(ltpc.getCp()),
            volume,
            exchangeMillis > 0 ? Instant.ofEpochMilli(exchangeMillis) : receivedAt,
            receivedAt
        );
    }

    private MarketUpdateV3.OHLC dailyOhlc(MarketUpdateV3.MarketOHLC marketOhlc) {
        if (marketOhlc == null || marketOhlc.getOhlc() == null || marketOhlc.getOhlc().isEmpty()) {
            return null;
        }
        return marketOhlc.getOhlc().stream()
            .filter(candle -> "1d".equalsIgnoreCase(candle.getInterval()))
            .findFirst()
            .orElse(marketOhlc.getOhlc().getFirst());
    }
}
