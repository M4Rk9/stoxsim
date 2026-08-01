package com.stoxsim.instrument.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stoxsim.market.provider.alpaca.AlpacaMarketDataProperties;

@Component
public class AlpacaInstrumentSyncJob implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        AlpacaInstrumentSyncJob.class
    );

    private final AlpacaInstrumentSyncService syncService;
    private final AlpacaMarketDataProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean synchronizedAtLeastOnce = new AtomicBoolean();

    public AlpacaInstrumentSyncJob(
        AlpacaInstrumentSyncService syncService,
        AlpacaMarketDataProperties properties
    ) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isInstrumentSyncOnStartup()
            || !properties.hasCredentials()) {
            LOGGER.info(
                "Alpaca startup instrument sync is disabled until credentials are configured"
            );
            return;
        }
        queue("startup");
    }

    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "America/New_York")
    public void synchronizeBeforeMarket() {
        if (properties.hasCredentials()) {
            queue("scheduled");
        }
    }

    @Scheduled(initialDelay = 600_000, fixedDelay = 1_800_000)
    public void recoverIncompleteStartupSync() {
        if (properties.hasCredentials() && !synchronizedAtLeastOnce.get()) {
            queue("recovery");
        }
    }

    private void queue(String trigger) {
        Thread.ofVirtual()
            .name("alpaca-us-instrument-sync")
            .start(() -> synchronize(trigger));
    }

    private void synchronize(String trigger) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            var result = syncService.synchronize();
            if (result.accepted() == 0) {
                throw new IllegalStateException(
                    "Alpaca instrument sync imported no instruments"
                );
            }
            synchronizedAtLeastOnce.set(true);
            LOGGER.info(
                "Alpaca {} instrument sync {} completed: accepted={}, ignored={}, deactivated={}, duration={}",
                trigger,
                result.syncId(),
                result.accepted(),
                result.ignored(),
                result.deactivated(),
                result.duration()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Alpaca {} instrument synchronization failed",
                trigger,
                exception
            );
        } finally {
            running.set(false);
        }
    }
}
