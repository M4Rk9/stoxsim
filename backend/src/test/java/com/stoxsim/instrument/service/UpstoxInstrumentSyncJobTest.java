package com.stoxsim.instrument.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

class UpstoxInstrumentSyncJobTest {

    @Test
    void startsSynchronizationFromApplicationRunner() throws Exception {
        var service = mock(UpstoxInstrumentSyncService.class);
        var properties = new UpstoxMarketDataProperties();
        properties.setPublicServingEnabled(true);
        when(service.synchronize()).thenReturn(new InstrumentSyncResult(
            UUID.randomUUID(),
            10,
            2,
            0,
            Duration.ofSeconds(1)
        ));
        var job = new UpstoxInstrumentSyncJob(service, properties, true);

        job.run(null);

        verify(service, timeout(2_000)).synchronize();
    }

    @Test
    void skipsStartupSynchronizationWhenDisabled() {
        var service = mock(UpstoxInstrumentSyncService.class);
        var properties = new UpstoxMarketDataProperties();
        properties.setPublicServingEnabled(true);
        var job = new UpstoxInstrumentSyncJob(service, properties, false);

        job.run(null);

        verifyNoInteractions(service);
    }

    @Test
    void skipsSynchronizationWhenPublicServingIsDisabled() {
        var service = mock(UpstoxInstrumentSyncService.class);
        var properties = new UpstoxMarketDataProperties();
        properties.setPublicServingEnabled(false);
        var job = new UpstoxInstrumentSyncJob(service, properties, true);

        job.run(null);
        job.synchronizeBeforeMarket();
        job.recoverIncompleteStartupSync();

        verifyNoInteractions(service);
    }
}
