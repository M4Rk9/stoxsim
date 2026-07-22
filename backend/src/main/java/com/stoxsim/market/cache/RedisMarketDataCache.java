package com.stoxsim.market.cache;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@Component
public class RedisMarketDataCache implements MarketDataCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMarketDataCache.class);
    private static final String NULL = "~";

    private final StringRedisTemplate redis;
    private final Duration quoteTtl;
    private final Duration candleTtl;

    public RedisMarketDataCache(
        StringRedisTemplate redis,
        UpstoxMarketDataProperties properties
    ) {
        this.redis = redis;
        this.quoteTtl = Duration.ofSeconds(properties.getQuoteTtlSeconds());
        this.candleTtl = Duration.ofMinutes(properties.getCandleTtlMinutes());
    }

    @Override
    public Optional<Quote> findQuote(InstrumentKey instrument) {
        try {
            String encoded = redis.opsForValue().get(quoteKey(instrument));
            return encoded == null ? Optional.empty() : Optional.of(decodeQuote(instrument, encoded));
        } catch (DataAccessException | IllegalArgumentException exception) {
            LOGGER.warn("Could not read quote cache for {}", instrument.value(), exception);
            return Optional.empty();
        }
    }

    @Override
    public void storeQuote(Quote quote) {
        try {
            redis.opsForValue().set(quoteKey(quote.instrument()), encodeQuote(quote), quoteTtl);
        } catch (DataAccessException exception) {
            LOGGER.warn("Could not store quote cache for {}", quote.instrument().value(), exception);
        }
    }

    @Override
    public Optional<List<Candle>> findCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        String from,
        String to
    ) {
        try {
            String encoded = redis.opsForValue().get(candleKey(instrument, interval, from, to));
            return encoded == null ? Optional.empty() : Optional.of(decodeCandles(encoded));
        } catch (DataAccessException | IllegalArgumentException exception) {
            LOGGER.warn("Could not read candle cache for {}", instrument.value(), exception);
            return Optional.empty();
        }
    }

    @Override
    public void storeCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        String from,
        String to,
        List<Candle> candles
    ) {
        try {
            redis.opsForValue().set(
                candleKey(instrument, interval, from, to),
                encodeCandles(candles),
                candleTtl
            );
        } catch (DataAccessException exception) {
            LOGGER.warn("Could not store candle cache for {}", instrument.value(), exception);
        }
    }

    private String quoteKey(InstrumentKey instrument) {
        return "market:quote:" + instrument.provider() + ":" + instrument.value();
    }

    private String candleKey(
        InstrumentKey instrument,
        CandleInterval interval,
        String from,
        String to
    ) {
        return "market:candles:" + instrument.provider() + ":" + instrument.value()
            + ":" + interval + ":" + from + ":" + to;
    }

    private String encodeQuote(Quote quote) {
        return String.join(
            "\t",
            decimal(quote.lastPrice()),
            decimal(quote.bid()),
            decimal(quote.ask()),
            decimal(quote.open()),
            decimal(quote.high()),
            decimal(quote.low()),
            decimal(quote.close()),
            decimal(quote.previousClose()),
            number(quote.volume()),
            instant(quote.exchangeTimestamp()),
            instant(quote.receivedAt())
        );
    }

    private Quote decodeQuote(InstrumentKey instrument, String encoded) {
        String[] parts = encoded.split("\\t", -1);
        if (parts.length != 11) {
            throw new IllegalArgumentException("Unexpected cached quote format");
        }
        return new Quote(
            instrument,
            decimal(parts[0]),
            decimal(parts[1]),
            decimal(parts[2]),
            decimal(parts[3]),
            decimal(parts[4]),
            decimal(parts[5]),
            decimal(parts[6]),
            decimal(parts[7]),
            longNumber(parts[8]),
            instant(parts[9]),
            instant(parts[10])
        );
    }

    private String encodeCandles(List<Candle> candles) {
        return candles.stream()
            .map(candle -> String.join(
                ",",
                Long.toString(candle.timestamp().toEpochMilli()),
                candle.open().toPlainString(),
                candle.high().toPlainString(),
                candle.low().toPlainString(),
                candle.close().toPlainString(),
                number(candle.volume())
            ))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private List<Candle> decodeCandles(String encoded) {
        if (encoded.isEmpty()) {
            return List.of();
        }
        List<Candle> candles = new ArrayList<>();
        for (String row : encoded.split("\n")) {
            String[] parts = row.split(",", -1);
            if (parts.length != 6) {
                throw new IllegalArgumentException("Unexpected cached candle format");
            }
            candles.add(new Candle(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                new BigDecimal(parts[1]),
                new BigDecimal(parts[2]),
                new BigDecimal(parts[3]),
                new BigDecimal(parts[4]),
                longNumber(parts[5])
            ));
        }
        return List.copyOf(candles);
    }

    private String decimal(BigDecimal value) {
        return value == null ? NULL : value.toPlainString();
    }

    private BigDecimal decimal(String value) {
        return NULL.equals(value) ? null : new BigDecimal(value);
    }

    private String number(Long value) {
        return value == null ? NULL : value.toString();
    }

    private Long longNumber(String value) {
        return NULL.equals(value) ? null : Long.valueOf(value);
    }

    private String instant(Instant value) {
        return value == null ? NULL : Long.toString(value.toEpochMilli());
    }

    private Instant instant(String value) {
        return NULL.equals(value) ? null : Instant.ofEpochMilli(Long.parseLong(value));
    }
}
