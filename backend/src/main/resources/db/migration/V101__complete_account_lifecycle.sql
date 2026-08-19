ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE app_user
SET email_verified_at = created_at
WHERE email_verified_at IS NULL;

ALTER TABLE refresh_token
    ADD COLUMN session_id UUID,
    ADD COLUMN session_started_at TIMESTAMPTZ,
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN user_agent VARCHAR(200);

UPDATE refresh_token
SET session_id = gen_random_uuid(),
    session_started_at = created_at,
    last_used_at = created_at,
    user_agent = 'Existing session'
WHERE session_id IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN session_started_at SET NOT NULL,
    ALTER COLUMN last_used_at SET NOT NULL,
    ALTER COLUMN user_agent SET NOT NULL;

CREATE INDEX idx_refresh_token_user_active
    ON refresh_token(user_id, last_used_at DESC)
    WHERE revoked_at IS NULL;

CREATE TABLE account_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_account_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_token_purpose CHECK (
        purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')
    )
);

CREATE INDEX idx_account_token_user_purpose
    ON account_token(user_id, purpose, created_at DESC);

CREATE TABLE account_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    event_type VARCHAR(48) NOT NULL,
    detail VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_account_event_user_created
    ON account_event(user_id, created_at DESC);

ALTER TABLE virtual_account
    DROP CONSTRAINT virtual_account_user_id_fkey,
    ADD CONSTRAINT virtual_account_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;
