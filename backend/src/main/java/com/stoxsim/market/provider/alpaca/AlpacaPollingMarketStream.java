package com.stoxsim.market.provider.alpaca;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.SubscriptionMode;

@Component
public class AlpacaPollingMarketStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        AlpacaPollingMarketStream.class
    );
    private static final int BATCH_SIZE = 100;

    private final AlpacaMarketDataProperties properties;
    private final AlpacaRestClient client;
    private final AlpacaQuoteMapper mapper;
    private final MeterRegistry meterRegistry;
    private final Map<InstrumentKey, CopyOnWriteArrayList<Consumer<MarketTick>>> listeners =
        new ConcurrentHashMap<>();

    public AlpacaPollingMarketStream(
        AlpacaMarketDataProperties properties,
        AlpacaRestClient client,
        AlpacaQuoteMapper mapper,
        MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
        Gauge.builder(
            "stoxsim.market_stream.subscriptions",
            listeners,
            Map::size
        ).tag("provider", "alpaca").register(meterRegistry);
    }

    public void subscribe(
        Set<InstrumentKey> instruments,
        SubscriptionMode mode,
        Consumer<MarketTick> listener
    ) {
        instruments.forEach(instrument -> listeners
            .computeIfAbsent(instrument, ignored -> new CopyOnWriteArrayList<>())
            .addIfAbsent(listener));
    }

    public void unsubscribe(Set<InstrumentKey> instruments) {
        instruments.forEach(listeners::remove);
    }

    @Scheduled(
        fixedDelayString = "${stoxsim.market-data.alpaca.polling-interval-millis:5000}"
    )
    public void poll() {
        if (!properties.isPollingEnabled()
            || !properties.hasCredentials()
            || listeners.isEmpty()) {
            return;
        }
        List<InstrumentKey> subscribed = new ArrayList<>(listeners.keySet());
        for (int offset = 0; offset < subscribed.size(); offset += BATCH_SIZE) {
            pollBatch(subscribed.subList(
                offset,
                Math.min(offset + BATCH_SIZE, subscribed.size())
            ));
        }
    }

    private void pollBatch(Collection<InstrumentKey> instruments) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Map<String, tools.jackson.databind.JsonNode> snapshots = client.getSnapshots(
                instruments.stream().map(InstrumentKey::value).toList()
            );
            Instant receivedAt = Instant.now();
            for (InstrumentKey instrument : instruments) {
                var snapshot = snapshots.get(instrument.value());
                if (snapshot == null) {
                    continue;
                }
                var quote = mapper.mapQuote(instrument, snapshot, receivedAt);
                if (quote == null) {
                    continue;
                }
                var registered = listeners.get(instrument);
                if (registered == null) {
                    continue;
                }
                MarketTick tick = new MarketTick(quote);
                for (Consumer<MarketTick> listener : registered) {
                    try {
                        listener.accept(tick);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Alpaca market tick listener failed", exception);
                    }
                }
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter(
                "stoxsim.market_provider.failures",
                "provider",
                "alpaca",
                "operation",
                "snapshot_poll"
            ).increment();
            LOGGER.warn("Could not poll subscribed Alpaca instruments", exception);
        } finally {
            sample.stop(meterRegistry.timer(
                "stoxsim.market_provider.latency",
                "provider",
                "alpaca",
                "operation",
                "snapshot_poll"
            ));
        }
    }
}
