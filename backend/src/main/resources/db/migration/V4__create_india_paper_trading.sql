CREATE TABLE market_holiday (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exchange VARCHAR(16) NOT NULL,
    holiday_date DATE NOT NULL,
    description VARCHAR(160) NOT NULL,
    CONSTRAINT uk_market_holiday_exchange_date UNIQUE (exchange, holiday_date),
    CONSTRAINT ck_market_holiday_exchange CHECK (exchange IN ('NSE', 'BSE'))
);

CREATE TABLE holding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instrument(id),
    quantity BIGINT NOT NULL,
    blocked_quantity BIGINT NOT NULL DEFAULT 0,
    average_price NUMERIC(19, 4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_holding_account_instrument UNIQUE (account_id, instrument_id),
    CONSTRAINT ck_holding_quantities CHECK (
        quantity >= 0 AND blocked_quantity >= 0 AND blocked_quantity <= quantity
    ),
    CONSTRAINT ck_holding_average_price CHECK (average_price >= 0)
);

CREATE TABLE paper_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instrument(id),
    idempotency_key VARCHAR(100) NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    validity VARCHAR(8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    quantity BIGINT NOT NULL,
    limit_price NUMERIC(19, 4),
    reserved_cash NUMERIC(19, 4) NOT NULL DEFAULT 0,
    execution_price NUMERIC(19, 4),
    executed_value NUMERIC(19, 4),
    rejection_reason VARCHAR(255),
    submitted_for_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMPTZ,
    CONSTRAINT uk_paper_order_idempotency UNIQUE (account_id, idempotency_key),
    CONSTRAINT ck_paper_order_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_paper_order_type CHECK (order_type IN ('MARKET', 'LIMIT')),
    CONSTRAINT ck_paper_order_product CHECK (product_type = 'DELIVERY'),
    CONSTRAINT ck_paper_order_validity CHECK (validity = 'DAY'),
    CONSTRAINT ck_paper_order_status CHECK (
        status IN ('OPEN', 'EXECUTED', 'CANCELLED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT ck_paper_order_quantity CHECK (quantity > 0),
    CONSTRAINT ck_paper_order_limit_price CHECK (
        (order_type = 'MARKET' AND limit_price IS NULL)
        OR (order_type = 'LIMIT' AND limit_price > 0)
    ),
    CONSTRAINT ck_paper_order_reserved_cash CHECK (reserved_cash >= 0)
);

CREATE TABLE trade (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES paper_order(id),
    account_id UUID NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instrument(id),
    side VARCHAR(8) NOT NULL,
    quantity BIGINT NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    gross_value NUMERIC(19, 4) NOT NULL,
    charges NUMERIC(19, 4) NOT NULL DEFAULT 0,
    executed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_trade_order UNIQUE (order_id),
    CONSTRAINT ck_trade_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_trade_quantity CHECK (quantity > 0),
    CONSTRAINT ck_trade_values CHECK (price > 0 AND gross_value > 0 AND charges >= 0)
);

CREATE TABLE account_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    order_id UUID REFERENCES paper_order(id),
    trade_id UUID REFERENCES trade(id),
    entry_type VARCHAR(24) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    balance_after NUMERIC(19, 4) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_account_ledger_type CHECK (entry_type IN ('TRADE_BUY', 'TRADE_SELL')),
    CONSTRAINT ck_account_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_account_ledger_amount CHECK (amount >= 0)
);

CREATE INDEX idx_holding_account ON holding(account_id);
CREATE INDEX idx_paper_order_account_created ON paper_order(account_id, created_at DESC);
CREATE INDEX idx_paper_order_open_instrument ON paper_order(instrument_id, submitted_for_date)
    WHERE status = 'OPEN';
CREATE INDEX idx_trade_account_executed ON trade(account_id, executed_at DESC);
CREATE INDEX idx_ledger_account_created ON account_ledger(account_id, created_at DESC);
CREATE INDEX idx_market_holiday_date ON market_holiday(holiday_date);
