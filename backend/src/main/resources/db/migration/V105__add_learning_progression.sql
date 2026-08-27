CREATE TABLE learner_progression (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    total_xp INTEGER NOT NULL DEFAULT 0,
    current_streak INTEGER NOT NULL DEFAULT 0,
    longest_streak INTEGER NOT NULL DEFAULT 0,
    last_check_in_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_learner_progression_xp CHECK (total_xp >= 0),
    CONSTRAINT ck_learner_progression_streaks CHECK (
        current_streak >= 0 AND longest_streak >= current_streak
    )
);

INSERT INTO learner_progression (user_id)
SELECT id FROM app_user
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE mission_completion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    mission_code VARCHAR(64) NOT NULL,
    xp_awarded INTEGER NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_completion_user_code UNIQUE (user_id, mission_code),
    CONSTRAINT ck_mission_completion_xp CHECK (xp_awarded > 0)
);

CREATE INDEX idx_mission_completion_user_time
    ON mission_completion(user_id, completed_at DESC);

CREATE TABLE achievement_unlock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    achievement_code VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_achievement_unlock_user_code UNIQUE (user_id, achievement_code)
);

CREATE INDEX idx_achievement_unlock_user_time
    ON achievement_unlock(user_id, unlocked_at DESC);
