ALTER TABLE user_subscription
    ADD COLUMN scenario_free_used INTEGER NOT NULL DEFAULT 0 CHECK (scenario_free_used >= 0),
    ADD COLUMN scenario_paid_used INTEGER NOT NULL DEFAULT 0 CHECK (scenario_paid_used >= 0),
    ADD COLUMN scenario_credit_period_end TIMESTAMPTZ;
UPDATE user_subscription SET scenario_credit_period_end = current_period_end
WHERE plan IN ('PLUS', 'PRO') AND subscription_status = 'ACTIVE';
CREATE TABLE scenario_run (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    request_payload TEXT NOT NULL,
    result_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, request_id)
);
