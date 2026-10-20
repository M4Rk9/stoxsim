-- Allow emergency closure overrides for every supported exchange.
ALTER TABLE market_holiday DROP CONSTRAINT ck_market_holiday_exchange;
ALTER TABLE market_holiday ADD CONSTRAINT ck_market_holiday_exchange
    CHECK (exchange IN ('NSE','BSE','NASDAQ','NYSE','NYSE_ARCA','AMEX','CBOE'));

-- Earlier observations did not record source provenance and may contain virtual fallback prices.
-- Preserve raw equity/benchmark values for audit, but never certify them retroactively.
UPDATE portfolio_history SET quality = 'LEGACY_UNVERIFIED' WHERE quality = 'OBSERVED';
