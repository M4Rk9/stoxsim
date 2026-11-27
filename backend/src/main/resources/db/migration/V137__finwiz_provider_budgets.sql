-- New operational counters. Existing global and per-user attempt counters retain history.
CREATE TABLE finwiz_provider_usage_daily (
    provider varchar(16) NOT NULL CHECK (provider IN ('GEMINI','GROQ','CLOUDFLARE','OPENROUTER')),
    usage_day date NOT NULL,
    provider_attempts integer NOT NULL DEFAULT 0 CHECK (provider_attempts >= 0),
    PRIMARY KEY (provider,usage_day)
);
ALTER TABLE finwiz_provider_usage_daily ADD COLUMN reported_total_tokens bigint NOT NULL DEFAULT 0 CHECK (reported_total_tokens>=0);
ALTER TABLE finwiz_provider_usage_daily ADD COLUMN responses_with_usage integer NOT NULL DEFAULT 0 CHECK (responses_with_usage>=0);
ALTER TABLE finwiz_usage_daily ADD COLUMN successful_requests integer NOT NULL DEFAULT 0 CHECK (successful_requests>=0);
CREATE TABLE finwiz_request_reservation (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    usage_day date NOT NULL,
    state varchar(12) NOT NULL CHECK (state IN ('PENDING','SUCCEEDED','FAILED')),
    expires_at timestamptz NOT NULL
);
CREATE INDEX idx_finwiz_request_pending ON finwiz_request_reservation(user_id,usage_day,expires_at) WHERE state='PENDING';
CREATE INDEX idx_finwiz_request_expiry ON finwiz_request_reservation(expires_at);
