CREATE TABLE derivative_contract (
    id UUID PRIMARY KEY,
    provider_key VARCHAR(64) NOT NULL,
    underlying_key VARCHAR(64) NOT NULL,
    symbol VARCHAR(100) NOT NULL,
    kind VARCHAR(4) NOT NULL CHECK (kind IN ('FUT', 'CE', 'PE')),
    expiry DATE NOT NULL,
    lot_size INTEGER NOT NULL CHECK (lot_size > 0),
    tick_size NUMERIC(19,6) NOT NULL CHECK (tick_size > 0),
    strike NUMERIC(19,4),
    sync_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    settlement_price NUMERIC(19,4),
    settlement_checked_at TIMESTAMPTZ,
    UNIQUE (provider_key, expiry),
    CHECK ((kind = 'FUT' AND strike IS NULL) OR (kind IN ('CE', 'PE') AND strike IS NOT NULL AND strike > 0))
);
CREATE INDEX derivative_catalog ON derivative_contract (underlying_key, expiry, kind, strike) WHERE active;

CREATE TABLE derivative_wallet (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    cash NUMERIC(19,4) NOT NULL,
    realized_pnl NUMERIC(19,4) NOT NULL DEFAULT 0
);
CREATE TABLE derivative_position (
    user_id UUID NOT NULL REFERENCES derivative_wallet(user_id) ON DELETE CASCADE,
    contract_id UUID NOT NULL REFERENCES derivative_contract(id),
    lots INTEGER NOT NULL CHECK (lots BETWEEN 1 AND 100),
    direction VARCHAR(5) NOT NULL CHECK (direction IN ('LONG', 'SHORT')),
    average_entry NUMERIC(19,8) NOT NULL CHECK (average_entry > 0),
    reserved_margin NUMERIC(19,4) NOT NULL CHECK (reserved_margin >= 0),
    PRIMARY KEY (user_id, contract_id)
);
CREATE INDEX derivative_expiry_positions ON derivative_position(contract_id);

CREATE TABLE derivative_fill (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES derivative_wallet(user_id) ON DELETE CASCADE,
    contract_id UUID NOT NULL REFERENCES derivative_contract(id),
    request_key UUID NOT NULL,
    action VARCHAR(10) NOT NULL CHECK (action IN ('OPEN_LONG', 'OPEN_SHORT', 'CLOSE', 'EXPIRY')),
    lots INTEGER NOT NULL CHECK (lots > 0),
    price NUMERIC(19,4) NOT NULL CHECK (price >= 0),
    cash_change NUMERIC(19,4) NOT NULL,
    realized_pnl NUMERIC(19,4) NOT NULL,
    filled_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, request_key)
);
CREATE INDEX derivative_fills_recent ON derivative_fill (user_id, filled_at DESC);
