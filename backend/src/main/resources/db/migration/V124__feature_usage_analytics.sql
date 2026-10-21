-- New observation coverage; never infer daily use from lifetime journey completion.
ALTER TABLE analytics_coverage ADD COLUMN feature_usage_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE product_activity DROP CONSTRAINT product_activity_event_name_check;
ALTER TABLE product_activity ADD CONSTRAINT product_activity_event_name_check CHECK (event_name IN
    ('ACTIVE', 'STOCK_OPENED', 'WATCHLIST_ADDED', 'ORDER_SUBMITTED', 'ORDER_EXECUTED',
     'FINWIZ_USED', 'SCENARIO_RUN', 'LEARNING_VIEWED', 'COMPETITIONS_VIEWED'));
