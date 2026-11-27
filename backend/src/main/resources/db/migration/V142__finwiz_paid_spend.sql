ALTER TABLE finwiz_provider_usage_daily DROP CONSTRAINT finwiz_provider_usage_daily_provider_check;
ALTER TABLE finwiz_provider_usage_daily ADD CHECK (provider IN ('GEMINI','GROQ','CLOUDFLARE','OPENROUTER','GEMINI_PAID'));
ALTER TABLE finwiz_provider_admission DROP CONSTRAINT finwiz_provider_admission_provider_check;
ALTER TABLE finwiz_provider_admission ADD CHECK (provider IN ('GEMINI','GROQ','CLOUDFLARE','OPENROUTER','GEMINI_PAID'));

CREATE TABLE finwiz_spend_guard (
    id integer PRIMARY KEY CHECK (id = 1),
    paused boolean NOT NULL DEFAULT false
);
INSERT INTO finwiz_spend_guard (id) VALUES (1);
CREATE TABLE finwiz_spend_attempt (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
    usage_day date NOT NULL,
    provider varchar(24) NOT NULL,
    model varchar(128) NOT NULL,
    depth varchar(8) NOT NULL CHECK (depth IN ('EXPANDED','FULL')),
    reserved_micros bigint NOT NULL CHECK (reserved_micros > 0),
    reported_micros bigint CHECK (reported_micros >= 0),
    input_tokens bigint CHECK (input_tokens >= 0),
    output_tokens bigint CHECK (output_tokens >= 0),
    reasoning_tokens bigint CHECK (reasoning_tokens >= 0),
    state varchar(8) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','REPORTED','UNKNOWN')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_finwiz_spend_day ON finwiz_spend_attempt(usage_day);
CREATE INDEX idx_finwiz_spend_user_day ON finwiz_spend_attempt(user_id,usage_day);
