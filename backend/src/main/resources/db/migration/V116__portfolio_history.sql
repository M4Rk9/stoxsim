CREATE TABLE portfolio_history (
    account_id UUID NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    observed_day DATE NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    currency VARCHAR(3) NOT NULL,
    equity NUMERIC(19,4),
    cash NUMERIC(19,4) NOT NULL,
    trade_cash NUMERIC(19,4) NOT NULL,
    capital NUMERIC(19,4) NOT NULL,
    quality VARCHAR(24) NOT NULL,
    benchmark NUMERIC(19,4),
    PRIMARY KEY (account_id, observed_day)
);
CREATE INDEX portfolio_history_retention ON portfolio_history(observed_day);
