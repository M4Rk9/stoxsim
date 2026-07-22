CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE virtual_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id),
    market_region VARCHAR(24) NOT NULL,
    currency CHAR(3) NOT NULL,
    available_cash NUMERIC(19, 4) NOT NULL,
    blocked_cash NUMERIC(19, 4) NOT NULL DEFAULT 0,
    realized_profit_loss NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_virtual_account_user_region UNIQUE (user_id, market_region),
    CONSTRAINT ck_virtual_account_region CHECK (market_region IN ('INDIA', 'UNITED_STATES')),
    CONSTRAINT ck_virtual_account_currency CHECK (currency IN ('INR', 'USD')),
    CONSTRAINT ck_virtual_account_cash_non_negative CHECK (available_cash >= 0 AND blocked_cash >= 0)
);

CREATE INDEX idx_virtual_account_user_id ON virtual_account(user_id);
