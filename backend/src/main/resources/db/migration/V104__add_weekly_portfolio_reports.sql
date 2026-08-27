CREATE TABLE weekly_report_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_weekly_report_preference_user UNIQUE (user_id)
);

CREATE INDEX idx_weekly_report_preference_enabled
    ON weekly_report_preference(enabled, user_id)
    WHERE enabled = TRUE;

CREATE TABLE weekly_portfolio_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    snapshot_json TEXT NOT NULL,
    delivery_status VARCHAR(24) NOT NULL,
    delivery_attempts INTEGER NOT NULL DEFAULT 0,
    delivery_attempted_at TIMESTAMPTZ,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_weekly_portfolio_report_user_period UNIQUE (user_id, period_end),
    CONSTRAINT ck_weekly_portfolio_report_period CHECK (period_end >= period_start),
    CONSTRAINT ck_weekly_portfolio_report_attempts CHECK (delivery_attempts >= 0),
    CONSTRAINT ck_weekly_portfolio_report_delivery CHECK (
        delivery_status IN ('PENDING', 'SENT', 'FAILED')
    )
);

CREATE INDEX idx_weekly_portfolio_report_user_period
    ON weekly_portfolio_report(user_id, period_end DESC);
