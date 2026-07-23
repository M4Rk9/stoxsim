package com.stoxsim.instrument.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UpstoxInstrumentSyncJobTest {

    @Test
    void startsSynchronizationFromApplicationRunner() throws Exception {
        var service = mock(UpstoxInstrumentSyncService.class);
        when(service.synchronize()).thenReturn(new InstrumentSyncResult(
            UUID.randomUUID(),
            10,
            2,
            0,
            Duration.ofSeconds(1)
        ));
        var job = new UpstoxInstrumentSyncJob(service, true);

        job.run(null);

        verify(service, timeout(2_000)).synchronize();
    }

    @Test
    void skipsStartupSynchronizationWhenDisabled() {
        var service = mock(UpstoxInstrumentSyncService.class);
        var job = new UpstoxInstrumentSyncJob(service, false);

        job.run(null);

        verifyNoInteractions(service);
    }
}
