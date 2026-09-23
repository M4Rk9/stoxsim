ALTER TABLE razorpay_test_subscription
    ADD COLUMN cancellation_status VARCHAR(16) NOT NULL DEFAULT 'NONE'
        CHECK (cancellation_status IN ('NONE','REQUESTED','CONFIRMED')),
    ADD COLUMN cancel_at TIMESTAMPTZ,
    ADD COLUMN cancellation_requested_at TIMESTAMPTZ;
