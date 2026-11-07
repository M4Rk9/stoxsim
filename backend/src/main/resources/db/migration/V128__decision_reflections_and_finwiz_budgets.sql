-- Private optional notes; database ownership constraints supplement authenticated services.
ALTER TABLE virtual_account ADD CONSTRAINT uk_virtual_account_owner UNIQUE (id, user_id);
ALTER TABLE paper_order ADD CONSTRAINT uk_paper_order_account UNIQUE (id, account_id);
CREATE TABLE decision_reflection (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    account_id uuid,
    order_id uuid,
    reason varchar(2000) NOT NULL CHECK (length(trim(reason)) > 0),
    assumptions varchar(2000) NOT NULL DEFAULT '',
    risks varchar(2000) NOT NULL DEFAULT '',
    expected_outcome varchar(2000) NOT NULL DEFAULT '',
    reflection varchar(2000) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    FOREIGN KEY (account_id, user_id) REFERENCES virtual_account(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (order_id, account_id) REFERENCES paper_order(id, account_id) ON DELETE CASCADE,
    CHECK (order_id IS NULL OR account_id IS NOT NULL)
);
CREATE INDEX idx_decision_reflection_user_time ON decision_reflection(user_id, created_at DESC, id);

-- Provider attempts are reserved before network calls and retained through downstream failures.
-- Never store prompts, private notes, answers or credentials in these counters.
CREATE TABLE finwiz_usage_daily (
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    usage_day date NOT NULL,
    provider_attempts integer NOT NULL DEFAULT 0 CHECK (provider_attempts >= 0),
    PRIMARY KEY (user_id, usage_day)
);
CREATE INDEX idx_finwiz_usage_day ON finwiz_usage_daily(usage_day);
CREATE TABLE finwiz_global_usage_daily (
    usage_day date PRIMARY KEY,
    provider_attempts integer NOT NULL DEFAULT 0 CHECK (provider_attempts >= 0),
    reported_total_tokens bigint NOT NULL DEFAULT 0 CHECK (reported_total_tokens >= 0),
    responses_with_usage integer NOT NULL DEFAULT 0 CHECK (responses_with_usage >= 0)
);
ALTER TABLE analytics_coverage ADD COLUMN finwiz_usage_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
