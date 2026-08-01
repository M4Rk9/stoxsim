package com.stoxsim.instrument.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stoxsim.instrument.provider.alpaca.AlpacaInstrumentMapper;
import com.stoxsim.market.provider.alpaca.AlpacaRestClient;

@Service
public class AlpacaInstrumentSyncService {

    private static final int BATCH_SIZE = 500;

    private final AlpacaRestClient client;
    private final AlpacaInstrumentMapper mapper;
    private final InstrumentBatchService batchService;

    public AlpacaInstrumentSyncService(
        AlpacaRestClient client,
        AlpacaInstrumentMapper mapper,
        InstrumentBatchService batchService
    ) {
        this.client = client;
        this.mapper = mapper;
        this.batchService = batchService;
    }

    public InstrumentSyncResult synchronize() {
        UUID syncId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        int accepted = 0;
        int ignored = 0;
        List<InstrumentSnapshot> batch = new ArrayList<>(BATCH_SIZE);
        var assets = client.getAssets();
        if (assets == null || !assets.isArray()) {
            throw new IllegalStateException(
                "Alpaca assets response must contain a JSON array"
            );
        }

        for (var asset : assets) {
            var snapshot = mapper.map(asset);
            if (snapshot.isEmpty()) {
                ignored++;
                continue;
            }
            batch.add(snapshot.get());
            accepted++;
            if (batch.size() == BATCH_SIZE) {
                batchService.upsert(List.copyOf(batch), syncId);
                batch.clear();
            }
        }
        batchService.upsert(List.copyOf(batch), syncId);
        int deactivated = batchService.deactivateMissing(
            AlpacaInstrumentMapper.PROVIDER,
            syncId
        );
        return new InstrumentSyncResult(
            syncId,
            accepted,
            ignored,
            deactivated,
            Duration.between(startedAt, Instant.now())
        );
    }
}
