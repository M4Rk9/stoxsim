package com.stoxsim.watchlist.domain;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.instrument.domain.TradableInstrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchlist_item")
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private TradableInstrument instrument;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WatchlistItem() {
    }

    public WatchlistItem(Watchlist watchlist, TradableInstrument instrument) {
        this.watchlist = watchlist;
        this.instrument = instrument;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public TradableInstrument getInstrument() {
        return instrument;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
