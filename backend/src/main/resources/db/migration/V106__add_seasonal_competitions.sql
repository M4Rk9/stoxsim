CREATE TABLE competition_season (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_competition_season_code UNIQUE (code),
    CONSTRAINT ck_competition_season_period CHECK (ends_at > starts_at)
);

CREATE TABLE competition_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id UUID NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    baseline_value NUMERIC(19,4) NOT NULL,
    latest_value NUMERIC(19,4) NOT NULL,
    return_percent NUMERIC(12,4) NOT NULL DEFAULT 0,
    data_status VARCHAR(24) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_competition_entry_season_user UNIQUE (season_id, user_id),
    CONSTRAINT ck_competition_entry_values CHECK (
        baseline_value > 0 AND latest_value >= 0
    ),
    CONSTRAINT ck_competition_entry_status CHECK (
        data_status IN ('LIVE', 'CLOSED', 'STALE', 'UNAVAILABLE')
    )
);

CREATE INDEX idx_competition_entry_standings
    ON competition_entry(season_id, return_percent DESC, joined_at, user_id);

CREATE TABLE private_league (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id UUID NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    invite_code_hash CHAR(64) NOT NULL,
    max_members INTEGER NOT NULL DEFAULT 25,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_private_league_invite_hash UNIQUE (invite_code_hash),
    CONSTRAINT ck_private_league_members CHECK (max_members BETWEEN 2 AND 50)
);

CREATE INDEX idx_private_league_owner
    ON private_league(owner_user_id, created_at DESC);

CREATE TABLE league_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID NOT NULL REFERENCES private_league(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    member_role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_league_member_league_user UNIQUE (league_id, user_id),
    CONSTRAINT ck_league_member_role CHECK (member_role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX idx_league_member_user
    ON league_member(user_id, joined_at DESC);
