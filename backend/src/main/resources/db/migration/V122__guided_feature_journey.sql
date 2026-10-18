CREATE TABLE guided_journey_event (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    step VARCHAR(32) NOT NULL CHECK (step IN ('FINWIZ', 'LEARNING', 'COMPETITIONS')),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, step)
);
