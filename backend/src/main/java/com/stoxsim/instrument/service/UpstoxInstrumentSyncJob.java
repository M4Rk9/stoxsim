package com.stoxsim.instrument.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UpstoxInstrumentSyncJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstoxInstrumentSyncJob.class);

    private final UpstoxInstrumentSyncService syncService;

    public UpstoxInstrumentSyncJob(UpstoxInstrumentSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "0 30 7 * * MON-FRI", zone = "Asia/Kolkata")
    public void synchronizeBeforeMarket() {
        try {
            var result = syncService.synchronize();
            LOGGER.info(
                "Upstox instrument sync {} completed: accepted={}, ignored={}, deactivated={}, duration={}",
                result.syncId(),
                result.accepted(),
                result.ignored(),
                result.deactivated(),
                result.duration()
            );
        } catch (Exception exception) {
            LOGGER.error("Upstox instrument synchronization failed", exception);
        }
    }
}
