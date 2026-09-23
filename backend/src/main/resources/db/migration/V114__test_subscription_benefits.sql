ALTER TABLE razorpay_test_subscription
    ADD COLUMN benefits_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN paid_through TIMESTAMPTZ,
    ADD COLUMN verified_paid_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN benefit_status VARCHAR(24) NOT NULL DEFAULT 'OFF',
    ADD COLUMN access_until TIMESTAMPTZ;
ALTER TABLE user_subscription ADD COLUMN test_access_until TIMESTAMPTZ;
ALTER TABLE virtual_account ADD COLUMN test_trading_until TIMESTAMPTZ;
