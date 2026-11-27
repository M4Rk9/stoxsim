-- New operational counters. Existing global and per-user attempt counters retain history.
CREATE TABLE finwiz_provider_usage_daily (
    provider varchar(16) NOT NULL CHECK (provider IN ('GEMINI','GROQ','CLOUDFLARE','OPENROUTER')),
    usage_day date NOT NULL,
    provider_attempts integer NOT NULL DEFAULT 0 CHECK (provider_attempts >= 0),
    PRIMARY KEY (provider,usage_day)
);
