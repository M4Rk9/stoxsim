package com.stoxsim.instrument.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UpstoxInstrumentSyncJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstoxInstrumentSyncJob.class);

    private final UpstoxInstrumentSyncService syncService;
    private final boolean syncOnStartup;
    private final AtomicBoolean running = new AtomicBoolean();

    public UpstoxInstrumentSyncJob(
        UpstoxInstrumentSyncService syncService,
        @Value("${stoxsim.market-data.upstox.instrument-sync-on-startup:true}")
        boolean syncOnStartup
    ) {
        this.syncService = syncService;
        this.syncOnStartup = syncOnStartup;
    }

    @Scheduled(cron = "0 30 7 * * MON-FRI", zone = "Asia/Kolkata")
    public void synchronizeBeforeMarket() {
        synchronize("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup() {
        if (!syncOnStartup) {
            LOGGER.info("Upstox startup instrument sync is disabled");
            return;
        }
        Thread.ofVirtual()
            .name("upstox-instrument-startup-sync")
            .start(() -> synchronize("startup"));
    }

    private void synchronize(String trigger) {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Skipping {} instrument sync because another sync is running", trigger);
            return;
        }
        try {
            var result = syncService.synchronize();
            LOGGER.info(
                "Upstox {} instrument sync {} completed: accepted={}, ignored={}, deactivated={}, duration={}",
                trigger,
                result.syncId(),
                result.accepted(),
                result.ignored(),
                result.deactivated(),
                result.duration()
            );
        } catch (Exception exception) {
            LOGGER.error("Upstox {} instrument synchronization failed", trigger, exception);
        } finally {
            running.set(false);
        }
    }
}
