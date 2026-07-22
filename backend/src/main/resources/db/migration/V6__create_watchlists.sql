CREATE TABLE watchlist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watchlist_user_name UNIQUE (user_id, name)
);

CREATE UNIQUE INDEX uk_watchlist_one_default
    ON watchlist (user_id)
    WHERE is_default = TRUE;

CREATE TABLE watchlist_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    watchlist_id UUID NOT NULL REFERENCES watchlist(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instrument(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watchlist_item UNIQUE (watchlist_id, instrument_id)
);

CREATE INDEX idx_watchlist_item_watchlist_created
    ON watchlist_item (watchlist_id, created_at DESC);
