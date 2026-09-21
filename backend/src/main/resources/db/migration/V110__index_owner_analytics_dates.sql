-- Bound signup/order aggregation to the requested period without loading entity rows.
CREATE INDEX idx_app_user_learner_created ON app_user(created_at)
    WHERE platform_role = 'USER';
CREATE INDEX idx_paper_order_created ON paper_order(created_at);
