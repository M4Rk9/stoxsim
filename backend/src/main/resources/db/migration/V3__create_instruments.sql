CREATE TABLE instrument (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(32) NOT NULL,
    instrument_key VARCHAR(160) NOT NULL,
    market_region VARCHAR(24) NOT NULL,
    exchange VARCHAR(16) NOT NULL,
    segment VARCHAR(32) NOT NULL,
    trading_symbol VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    isin VARCHAR(16),
    instrument_type VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    lot_size INTEGER NOT NULL,
    tick_size NUMERIC(19, 6) NOT NULL,
    security_type VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_sync_id UUID NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_instrument_provider_key UNIQUE (provider, instrument_key),
    CONSTRAINT ck_instrument_market_region CHECK (market_region IN ('INDIA', 'UNITED_STATES')),
    CONSTRAINT ck_instrument_exchange CHECK (exchange IN ('NSE', 'BSE', 'NASDAQ', 'NYSE')),
    CONSTRAINT ck_instrument_type CHECK (instrument_type IN ('EQUITY', 'ETF', 'INDEX')),
    CONSTRAINT ck_instrument_currency CHECK (currency IN ('INR', 'USD')),
    CONSTRAINT ck_instrument_lot_size CHECK (lot_size > 0),
    CONSTRAINT ck_instrument_tick_size CHECK (tick_size >= 0)
);

CREATE INDEX idx_instrument_region_active ON instrument(market_region, active);
CREATE INDEX idx_instrument_exchange_symbol ON instrument(exchange, trading_symbol);
CREATE INDEX idx_instrument_isin ON instrument(isin);
