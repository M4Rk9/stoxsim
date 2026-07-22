package com.stoxsim.instrument.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;

@Service
public class InstrumentBatchService {

    private final TradableInstrumentRepository repository;

    public InstrumentBatchService(TradableInstrumentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(List<InstrumentSnapshot> snapshots, UUID syncId) {
        if (snapshots.isEmpty()) {
            return;
        }

        String provider = snapshots.getFirst().provider();
        List<String> keys = snapshots.stream().map(InstrumentSnapshot::instrumentKey).toList();
        Map<String, TradableInstrument> existing = repository
            .findAllByProviderAndInstrumentKeyIn(provider, keys)
            .stream()
            .collect(Collectors.toMap(TradableInstrument::getInstrumentKey, Function.identity()));

        Instant now = Instant.now();
        List<TradableInstrument> instruments = snapshots.stream()
            .map(snapshot -> {
                var instrument = existing.get(snapshot.instrumentKey());
                if (instrument == null) {
                    return new TradableInstrument(snapshot, syncId, now);
                }
                instrument.apply(snapshot, syncId, now);
                return instrument;
            })
            .toList();

        repository.saveAll(instruments);
    }

    @Transactional
    public int deactivateMissing(String provider, UUID syncId) {
        return repository.deactivateMissing(provider, syncId);
    }
}
