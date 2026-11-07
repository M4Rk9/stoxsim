-- Optional preferences: no questionnaire, inferred profile, or historical activity backfill.
CREATE TABLE learner_profile (
    user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    experience_level varchar(16) CHECK (experience_level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    learning_objective varchar(24) CHECK (learning_objective IN ('BASICS','FUNDAMENTALS','DIVERSIFICATION','DISCIPLINE')),
    preferred_market varchar(24) CHECK (preferred_market IN ('INDIA','UNITED_STATES')),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE analytics_coverage ADD COLUMN learner_usage_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE product_activity DROP CONSTRAINT product_activity_event_name_check;
ALTER TABLE product_activity ADD CONSTRAINT product_activity_event_name_check CHECK (event_name IN
    ('ACTIVE','STOCK_OPENED','WATCHLIST_ADDED','ORDER_SUBMITTED','ORDER_EXECUTED',
     'FINWIZ_USED','SCENARIO_RUN','LEARNING_VIEWED','COMPETITIONS_VIEWED','RECOMMENDATION_USED','REFLECTION_SAVED'));
