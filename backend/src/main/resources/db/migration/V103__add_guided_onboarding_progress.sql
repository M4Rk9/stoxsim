ALTER TABLE app_user
    ADD COLUMN onboarding_intro_completed_at TIMESTAMPTZ,
    ADD COLUMN first_order_placed_at TIMESTAMPTZ,
    ADD COLUMN onboarding_dismissed_at TIMESTAMPTZ;

UPDATE app_user AS learner
SET first_order_placed_at = first_order.first_order_at
FROM (
    SELECT account.user_id, MIN(paper_order.created_at) AS first_order_at
    FROM paper_order
    INNER JOIN virtual_account AS account ON account.id = paper_order.account_id
    GROUP BY account.user_id
) AS first_order
WHERE learner.id = first_order.user_id;
