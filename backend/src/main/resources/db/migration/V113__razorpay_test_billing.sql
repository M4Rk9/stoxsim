-- Test-mode billing is deliberately separate from paid entitlements and portfolios.
CREATE TABLE razorpay_test_subscription (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    request_key UUID NOT NULL,
    plan VARCHAR(8) NOT NULL CHECK (plan IN ('PLUS','PRO')),
    provider_plan_id VARCHAR(120) NOT NULL,
    provider_id VARCHAR(120) UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATING',
    current_period_end TIMESTAMPTZ,
    paid_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, request_key)
);
CREATE UNIQUE INDEX razorpay_test_one_open ON razorpay_test_subscription(user_id)
    WHERE status NOT IN ('cancelled','completed','expired');
CREATE TABLE razorpay_test_event (
    event_id VARCHAR(128) PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES razorpay_test_subscription(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
