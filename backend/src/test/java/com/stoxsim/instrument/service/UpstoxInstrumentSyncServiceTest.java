package com.stoxsim.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.stoxsim.instrument.provider.upstox.UpstoxInstrumentMapper;
import com.stoxsim.instrument.provider.upstox.UpstoxInstrumentMasterClient;

class UpstoxInstrumentSyncServiceTest {

    @Test
    void synchronizesNseAndBseSourcesBeforeDeactivatingMissingInstruments() throws Exception {
        var client = mock(UpstoxInstrumentMasterClient.class);
        var batches = mock(InstrumentBatchService.class);
        when(client.sources()).thenReturn(List.of("nse", "bse"));
        when(client.download("nse")).thenReturn(json("""
            [
              {
                "segment": "NSE_EQ",
                "instrument_key": "NSE_EQ|INE002A01018",
                "trading_symbol": "RELIANCE",
                "name": "Reliance Industries Limited"
              },
              {
                "segment": "NSE_FO",
                "instrument_key": "NSE_FO|123",
                "trading_symbol": "RELIANCE FUT",
                "name": "Reliance Futures"
              }
            ]
            """));
        when(client.download("bse")).thenReturn(json("""
            [
              {
                "segment": "BSE_INDEX",
                "instrument_key": "BSE_INDEX|SENSEX",
                "trading_symbol": "SENSEX",
                "name": "SENSEX"
              }
            ]
            """));

        var service = new UpstoxInstrumentSyncService(
            client,
            new UpstoxInstrumentMapper(),
            batches,
            new ObjectMapper()
        );

        var result = service.synchronize();

        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.ignored()).isEqualTo(1);
        verify(batches, times(2)).upsert(anyList(), any());
        verify(batches).deactivateMissing(
            UpstoxInstrumentMapper.PROVIDER,
            result.syncId()
        );
    }

    private ByteArrayInputStream json(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
