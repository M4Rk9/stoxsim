-- Google identity is keyed by the stable subject, never by email.
-- One Google identity per local account. Deleting an account removes its link.
CREATE TABLE google_identity (
    subject VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
