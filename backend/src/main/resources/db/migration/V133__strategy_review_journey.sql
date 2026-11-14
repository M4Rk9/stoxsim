-- Additive: older clients can keep writing reflections without erasing strategy fields.
ALTER TABLE decision_reflection
    ADD COLUMN strategy_exchange varchar(20) NOT NULL DEFAULT '',
    ADD COLUMN strategy_symbol varchar(100) NOT NULL DEFAULT '',
    ADD COLUMN horizon varchar(200) NOT NULL DEFAULT '',
    ADD COLUMN target varchar(500) NOT NULL DEFAULT '',
    ADD COLUMN invalidation varchar(500) NOT NULL DEFAULT '',
    ADD COLUMN review_on date,
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN strategy_created_at timestamptz;
CREATE INDEX idx_reflection_due ON decision_reflection(user_id, review_on) WHERE reviewed_at IS NULL;
ALTER TABLE analytics_coverage ADD COLUMN strategy_journey_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE product_activity DROP CONSTRAINT product_activity_event_name_check;
ALTER TABLE product_activity ADD CONSTRAINT product_activity_event_name_check CHECK (event_name IN
    ('ACTIVE','STOCK_OPENED','WATCHLIST_ADDED','ORDER_SUBMITTED','ORDER_EXECUTED',
     'FINWIZ_USED','SCENARIO_RUN','LEARNING_VIEWED','COMPETITIONS_VIEWED','RECOMMENDATION_USED',
     'REFLECTION_SAVED','STRATEGY_CREATED','REVIEW_COMPLETED'));
