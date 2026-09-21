-- No historical activity is inferred. This timestamp defines observation coverage.
CREATE TABLE analytics_coverage (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO analytics_coverage(singleton) VALUES (true);

-- One first occurrence per learner/event/UTC day: bounded, idempotent storage.
CREATE TABLE product_activity (
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    event_day date NOT NULL,
    event_name varchar(32) NOT NULL CHECK (event_name IN
        ('ACTIVE', 'STOCK_OPENED', 'WATCHLIST_ADDED', 'ORDER_SUBMITTED', 'ORDER_EXECUTED')),
    schema_version smallint NOT NULL DEFAULT 1 CHECK (schema_version = 1),
    first_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, event_day, event_name),
    CHECK (event_day = (first_at AT TIME ZONE 'UTC')::date)
);
CREATE INDEX idx_product_activity_day_user ON product_activity(event_day, user_id);
