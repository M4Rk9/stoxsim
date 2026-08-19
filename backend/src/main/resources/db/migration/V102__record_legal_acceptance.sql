ALTER TABLE app_user
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version VARCHAR(32),
    ADD COLUMN privacy_version VARCHAR(32);

ALTER TABLE app_user
    ADD CONSTRAINT app_user_legal_acceptance_complete
    CHECK (
        (terms_accepted_at IS NULL AND terms_version IS NULL AND privacy_version IS NULL)
        OR
        (terms_accepted_at IS NOT NULL AND terms_version IS NOT NULL AND privacy_version IS NOT NULL)
    );
