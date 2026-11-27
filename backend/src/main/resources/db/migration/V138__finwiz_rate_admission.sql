ALTER TABLE finwiz_provider_usage_daily ADD COLUMN reserved_tokens bigint NOT NULL DEFAULT 0 CHECK(reserved_tokens>=0);
CREATE TABLE finwiz_provider_admission (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider varchar(16) NOT NULL CHECK (provider IN ('GEMINI','GROQ','CLOUDFLARE','OPENROUTER')),
    admitted_at timestamptz NOT NULL DEFAULT now(),
    estimated_tokens integer NOT NULL CHECK (estimated_tokens>0)
);
CREATE INDEX idx_finwiz_provider_admission_window ON finwiz_provider_admission(provider,admitted_at);
