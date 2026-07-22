package com.stoxsim.instrument.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import com.stoxsim.instrument.provider.upstox.UpstoxInstrumentMapper;
import com.stoxsim.instrument.provider.upstox.UpstoxInstrumentMasterClient;

@Service
public class UpstoxInstrumentSyncService {

    private static final int BATCH_SIZE = 500;

    private final UpstoxInstrumentMasterClient client;
    private final UpstoxInstrumentMapper mapper;
    private final InstrumentBatchService batchService;
    private final ObjectMapper objectMapper;

    public UpstoxInstrumentSyncService(
        UpstoxInstrumentMasterClient client,
        UpstoxInstrumentMapper mapper,
        InstrumentBatchService batchService,
        ObjectMapper objectMapper
    ) {
        this.client = client;
        this.mapper = mapper;
        this.batchService = batchService;
        this.objectMapper = objectMapper;
    }

    public InstrumentSyncResult synchronize() throws IOException, InterruptedException {
        UUID syncId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        int accepted = 0;
        int ignored = 0;
        List<InstrumentSnapshot> batch = new ArrayList<>(BATCH_SIZE);

        try (
            var input = client.download();
            var parser = objectMapper.getFactory().createParser(input)
        ) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Upstox instrument master must contain a JSON array");
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                var node = objectMapper.readTree(parser);
                var snapshot = mapper.map(node);
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
        }

        batchService.upsert(List.copyOf(batch), syncId);
        int deactivated = batchService.deactivateMissing(UpstoxInstrumentMapper.PROVIDER, syncId);
        return new InstrumentSyncResult(
            syncId,
            accepted,
            ignored,
            deactivated,
            Duration.between(startedAt, Instant.now())
        );
    }
}
