ALTER TABLE virtual_account
    ADD COLUMN account_kind VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN sandbox_plan VARCHAR(16),
    ADD COLUMN sandbox_slot INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN account_label VARCHAR(80) NOT NULL DEFAULT 'Standard portfolio',
    ADD COLUMN starting_capital NUMERIC(19,4),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN leaderboard_eligible BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE virtual_account
SET starting_capital = CASE market_region
        WHEN 'INDIA' THEN 500000.0000
        WHEN 'UNITED_STATES' THEN 10000.0000
    END;

ALTER TABLE virtual_account
    ALTER COLUMN starting_capital SET NOT NULL,
    DROP CONSTRAINT uk_virtual_account_user_region,
    ADD CONSTRAINT ck_virtual_account_starting_capital
        CHECK (starting_capital > 0),
    ADD CONSTRAINT ck_virtual_account_scope CHECK (
        (
            account_kind = 'STANDARD'
            AND sandbox_plan IS NULL
            AND sandbox_slot = 0
            AND leaderboard_eligible
        ) OR (
            account_kind = 'SANDBOX'
            AND sandbox_plan IN ('PLUS', 'PRO')
            AND sandbox_slot BETWEEN 1 AND 5
            AND NOT leaderboard_eligible
        )
    );

ALTER TABLE virtual_account
    ALTER COLUMN account_kind DROP DEFAULT,
    ALTER COLUMN sandbox_slot DROP DEFAULT,
    ALTER COLUMN account_label DROP DEFAULT,
    ALTER COLUMN active DROP DEFAULT,
    ALTER COLUMN leaderboard_eligible DROP DEFAULT;

CREATE UNIQUE INDEX uk_virtual_account_user_region_scope_slot
    ON virtual_account(user_id, market_region, account_kind, sandbox_slot);

CREATE INDEX idx_virtual_account_user_kind_active
    ON virtual_account(user_id, account_kind, active);

CREATE TABLE user_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    plan VARCHAR(16) NOT NULL,
    subscription_status VARCHAR(24) NOT NULL,
    billing_provider VARCHAR(32),
    provider_customer_reference VARCHAR(120),
    provider_subscription_reference VARCHAR(120),
    current_period_end TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_subscription_user UNIQUE (user_id),
    CONSTRAINT ck_user_subscription_plan CHECK (plan IN ('FREE', 'PLUS', 'PRO')),
    CONSTRAINT ck_user_subscription_status CHECK (
        subscription_status IN ('ACTIVE', 'PAST_DUE', 'CANCELED')
    ),
    CONSTRAINT ck_user_subscription_provider_references CHECK (
        (
            billing_provider IS NULL
            AND provider_customer_reference IS NULL
            AND provider_subscription_reference IS NULL
        ) OR (
            billing_provider IS NOT NULL
            AND provider_customer_reference IS NOT NULL
            AND provider_subscription_reference IS NOT NULL
        )
    )
);

CREATE UNIQUE INDEX uk_user_subscription_provider_reference
    ON user_subscription(billing_provider, provider_subscription_reference)
    WHERE provider_subscription_reference IS NOT NULL;

INSERT INTO user_subscription (user_id, plan, subscription_status)
SELECT id, 'FREE', 'ACTIVE'
FROM app_user;
