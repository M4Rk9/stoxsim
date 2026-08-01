ALTER TABLE instrument
    DROP CONSTRAINT IF EXISTS ck_instrument_exchange;

ALTER TABLE instrument
    ADD CONSTRAINT ck_instrument_exchange
    CHECK (exchange IN (
        'NSE',
        'BSE',
        'NASDAQ',
        'NYSE',
        'NYSE_ARCA',
        'AMEX',
        'CBOE'
    ));
