package com.stoxsim.market.provider.upstox;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.upstox.feeder.MarketDataStreamerV3;
import com.upstox.feeder.constants.Mode;
import com.stoxsim.market.cache.MarketDataCache;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.websocket.MarketTickBroadcaster;

import jakarta.annotation.PreDestroy;

@Component
public class UpstoxMarketStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstoxMarketStream.class);

    private final UpstoxMarketDataProperties properties;
    private final UpstoxClientFactory clientFactory;
    private final UpstoxStreamMapper mapper;
    private final MarketDataCache cache;
    private final MarketTickBroadcaster broadcaster;
    private final Set<Consumer<MarketTick>> listeners = new CopyOnWriteArraySet<>();
    private final Map<String, Integer> referenceCounts = new ConcurrentHashMap<>();
    private final Map<String, Mode> desiredModes = new ConcurrentHashMap<>();

    private volatile MarketDataStreamerV3 streamer;
    private volatile boolean connected;

    public UpstoxMarketStream(
        UpstoxMarketDataProperties properties,
        UpstoxClientFactory clientFactory,
        UpstoxStreamMapper mapper,
        MarketDataCache cache,
        MarketTickBroadcaster broadcaster
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.mapper = mapper;
        this.cache = cache;
        this.broadcaster = broadcaster;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void connectAfterStartup() {
        if (!properties.isStreamEnabled()) {
            LOGGER.info("Upstox market stream is disabled");
            return;
        }
        if (!properties.hasAnalyticsToken()) {
            LOGGER.warn("Upstox market stream was enabled without UPSTOX_ANALYTICS_TOKEN");
            return;
        }
        if (streamer != null) {
            return;
        }

        try {
            Set<String> initialKeys = Set.copyOf(properties.getInitialInstrumentKeys());
            streamer = new MarketDataStreamerV3(clientFactory.createClient(), initialKeys, Mode.FULL);
            streamer.setOnMarketUpdateListener(this::handleUpdate);
            streamer.setOnOpenListener(this::handleOpen);
            streamer.setOnErrorListener(error -> LOGGER.error("Upstox market stream error", error));
            streamer.setOnCloseListener((status, reason) -> {
                connected = false;
                LOGGER.warn("Upstox market stream closed: status={}, reason={}", status, reason);
            });
            streamer.setOnReconnectingListener(message -> LOGGER.warn("Upstox reconnecting: {}", message));
            streamer.setOnAutoReconnectStoppedListener(
                message -> LOGGER.error("Upstox auto-reconnect stopped: {}", message)
            );
            streamer.autoReconnect(true, 10, 10);
            streamer.connect();
        } catch (RuntimeException exception) {
            streamer = null;
            connected = false;
            LOGGER.error("Could not start Upstox market stream", exception);
        }
    }

    public void subscribe(
        Set<InstrumentKey> instruments,
        SubscriptionMode mode,
        Consumer<MarketTick> listener
    ) {
        if (listener != null) {
            listeners.add(listener);
        }
        Mode upstoxMode = toMode(mode);
        Set<String> newKeys = instruments.stream()
            .map(InstrumentKey::value)
            .filter(key -> referenceCounts.merge(key, 1, Integer::sum) == 1)
            .peek(key -> desiredModes.put(key, upstoxMode))
            .collect(Collectors.toSet());

        MarketDataStreamerV3 current = streamer;
        if (connected && current != null && !newKeys.isEmpty()) {
            current.subscribe(newKeys, upstoxMode);
        }
    }

    public void unsubscribe(Set<InstrumentKey> instruments) {
        Set<String> removedKeys = instruments.stream()
            .map(InstrumentKey::value)
            .filter(this::release)
            .collect(Collectors.toSet());

        MarketDataStreamerV3 current = streamer;
        if (connected && current != null && !removedKeys.isEmpty()) {
            current.unsubscribe(removedKeys);
        }
    }

    private boolean release(String key) {
        var removed = new AtomicBoolean(false);
        referenceCounts.computeIfPresent(key, (ignored, count) -> {
            if (count <= 1) {
                removed.set(true);
                return null;
            }
            return count - 1;
        });
        if (removed.get()) {
            desiredModes.remove(key);
        }
        return removed.get();
    }

    private void handleOpen() {
        connected = true;
        LOGGER.info("Upstox market stream connected");
        MarketDataStreamerV3 current = streamer;
        desiredModes.forEach((key, mode) -> current.subscribe(Set.of(key), mode));
    }

    private void handleUpdate(com.upstox.feeder.MarketUpdateV3 update) {
        for (var quote : mapper.map(update)) {
            cache.storeQuote(quote);
            broadcaster.broadcast(quote);
            var tick = new MarketTick(quote);
            listeners.forEach(listener -> {
                try {
                    listener.accept(tick);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Market tick listener failed", exception);
                }
            });
        }
    }

    private Mode toMode(SubscriptionMode mode) {
        return mode == SubscriptionMode.LAST_TRADED_PRICE ? Mode.LTPC : Mode.FULL;
    }

    @PreDestroy
    public synchronized void disconnect() {
        MarketDataStreamerV3 current = streamer;
        streamer = null;
        connected = false;
        if (current != null) {
            try {
                current.disconnect();
            } catch (RuntimeException exception) {
                LOGGER.debug("Upstox stream was already closed", exception);
            }
        }
    }
}
