package com.stoxsim.instrument.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instrument")
public class TradableInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "instrument_key", nullable = false, length = 160)
    private String instrumentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_region", nullable = false, length = 24)
    private MarketRegion marketRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MarketExchange exchange;

    @Column(nullable = false, length = 32)
    private String segment;

    @Column(name = "trading_symbol", nullable = false, length = 100)
    private String tradingSymbol;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 16)
    private String isin;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 16)
    private InstrumentType instrumentType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "lot_size", nullable = false)
    private int lotSize;

    @Column(name = "tick_size", nullable = false, precision = 19, scale = 6)
    private BigDecimal tickSize;

    @Column(name = "security_type", length = 40)
    private String securityType;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_seen_sync_id", nullable = false)
    private UUID lastSeenSyncId;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected TradableInstrument() {
    }

    public TradableInstrument(InstrumentSnapshot snapshot, UUID syncId, Instant syncedAt) {
        apply(snapshot, syncId, syncedAt);
    }

    public void apply(InstrumentSnapshot snapshot, UUID syncId, Instant timestamp) {
        this.provider = snapshot.provider();
        this.instrumentKey = snapshot.instrumentKey();
        this.marketRegion = snapshot.marketRegion();
        this.exchange = snapshot.exchange();
        this.segment = snapshot.segment();
        this.tradingSymbol = snapshot.tradingSymbol();
        this.name = snapshot.name();
        this.isin = snapshot.isin();
        this.instrumentType = snapshot.instrumentType();
        this.currency = snapshot.currency();
        this.lotSize = snapshot.lotSize();
        this.tickSize = snapshot.tickSize();
        this.securityType = snapshot.securityType();
        this.active = true;
        this.lastSeenSyncId = syncId;
        this.syncedAt = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getInstrumentKey() {
        return instrumentKey;
    }

    public MarketRegion getMarketRegion() {
        return marketRegion;
    }

    public MarketExchange getExchange() {
        return exchange;
    }

    public String getSegment() {
        return segment;
    }

    public String getTradingSymbol() {
        return tradingSymbol;
    }

    public String getName() {
        return name;
    }

    public String getIsin() {
        return isin;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public String getCurrency() {
        return currency;
    }

    public int getLotSize() {
        return lotSize;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    public boolean isActive() {
        return active;
    }
}
