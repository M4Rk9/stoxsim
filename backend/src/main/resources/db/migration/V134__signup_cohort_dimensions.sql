-- No historical attribution is inferred from current sessions or identity links.
CREATE TABLE signup_attribution (
    user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    source varchar(24) NOT NULL CHECK (source IN ('DIRECT','SEARCH','SOCIAL','REFERRAL','UNKNOWN')),
    device varchar(24) NOT NULL CHECK (device IN ('ANDROID_APP','MOBILE_WEB','DESKTOP_WEB','UNKNOWN')),
    method varchar(12) NOT NULL CHECK (method IN ('EMAIL','GOOGLE')),
    recorded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE analytics_coverage ADD COLUMN signup_dimensions_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
